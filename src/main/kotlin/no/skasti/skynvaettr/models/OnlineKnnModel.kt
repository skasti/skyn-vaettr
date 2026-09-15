package no.skasti.skynvaettr.models

import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sqrt
import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation

/**
 * Small dependency-free online model used as the first expectation-learning baseline.
 *
 * The model consumes the same [Representation] produced by the processing graph as inference and
 * training. Nearest-neighbour lookup compares the complete ordered representation with a
 * dynamic-time-warping-style distance, preserving the binding between each position's signal
 * identity, value and relative time instead of collapsing the sequence through mean pooling.
 *
 * Training weights are retained on individual examples and participate directly in neighbour
 * weighting. This allows multiple self-supervised objectives to share a model without fractional
 * objective weights silently collapsing to an identical stored-example count.
 *
 * This remains deliberately simpler than a learned attention/QKV model. Its purpose is to provide a
 * sequence-aware baseline while keeping the model and expectation lifecycle independently replaceable.
 * Its latent output has one position: [predicted numeric value, confidence]. A decoder assigns that
 * output domain meaning.
 */
class OnlineKnnModel(
    private val neighbours: Int = 8,
    private val maxExamples: Int = 512,
    private val confidenceExamples: Int = 12,
) : TrainableModel {
    private data class Example(
        val input: Representation,
        val target: Double,
        val weight: Double,
    )

    private val examples = ArrayDeque<Example>()
    private var learnedInputDimensions: Int? = null

    init {
        require(neighbours > 0) { "neighbours must be positive" }
        require(maxExamples > 0) { "maxExamples must be positive" }
        require(confidenceExamples > 0) { "confidenceExamples must be positive" }
    }

    val trainingExampleCount: Int
        get() = examples.size

    override fun supports(input: Representation): Boolean =
        learnedInputDimensions == null || learnedInputDimensions == input.dimensions

    override fun forward(input: Representation): Representation {
        require(supports(input)) {
            "Expected representation width $learnedInputDimensions, got ${input.dimensions}"
        }
        if (examples.isEmpty()) {
            return output(value = 0.0, confidence = 0.0)
        }

        val nearest = examples
            .map { example -> example to representationDistance(input, example.input) }
            .sortedBy { it.second }
            .take(neighbours)

        var weightedTarget = 0.0
        var weightTotal = 0.0
        nearest.forEach { (example, distance) ->
            val neighbourWeight = example.weight / (distance + 1e-6)
            weightedTarget += example.target * neighbourWeight
            weightTotal += neighbourWeight
        }

        val nearestDistance = nearest.first().second
        val retainedWeight = examples.sumOf { it.weight }
        val experienceConfidence = (retainedWeight / confidenceExamples).coerceIn(0.0, 1.0)
        val localityConfidence = exp(-nearestDistance).coerceIn(0.0, 1.0)
        return output(
            value = weightedTarget / weightTotal,
            confidence = (experienceConfidence * localityConfidence).coerceIn(0.0, 1.0),
        )
    }

    override fun train(
        input: Representation,
        target: Representation,
        weight: Double,
    ) {
        require(weight.isFinite() && weight >= 0.0) { "weight must be finite and non-negative" }
        if (weight == 0.0) return
        require(target.positions == 1 && target.dimensions >= 1) {
            "KNN numeric training target must contain one position with at least one dimension"
        }

        if (learnedInputDimensions == null) learnedInputDimensions = input.dimensions
        require(supports(input)) {
            "Expected representation width $learnedInputDimensions, got ${input.dimensions}"
        }

        examples.addLast(
            Example(
                input = copyOf(input),
                target = target[0][0],
                weight = weight,
            ),
        )
        while (examples.size > maxExamples) examples.removeFirst()
    }

    /**
     * Sequence-aware distance supporting representations with different position counts.
     *
     * Dynamic time warping is useful here because early runtime history contains fewer positions than
     * a fully populated history window. Matching is monotonic, so swapping otherwise identical
     * positions no longer produces the same representation as it did under mean pooling.
     */
    private fun representationDistance(left: Representation, right: Representation): Double {
        require(left.dimensions == right.dimensions)

        var previous = DoubleArray(right.positions + 1) { Double.POSITIVE_INFINITY }
        previous[0] = 0.0

        for (leftIndex in 1..left.positions) {
            val current = DoubleArray(right.positions + 1) { Double.POSITIVE_INFINITY }
            for (rightIndex in 1..right.positions) {
                val cost = embeddingDistance(left[leftIndex - 1], right[rightIndex - 1])
                current[rightIndex] = cost + min(
                    previous[rightIndex],
                    min(current[rightIndex - 1], previous[rightIndex - 1]),
                )
            }
            previous = current
        }

        return previous[right.positions] / maxOf(left.positions, right.positions).toDouble()
    }

    private fun embeddingDistance(left: Embedding, right: Embedding): Double {
        require(left.dimensions == right.dimensions)
        val meanSquaredDifference =
            (0 until left.dimensions).sumOf { dimension ->
                val delta = left[dimension] - right[dimension]
                delta * delta
            } / left.dimensions.toDouble()
        return sqrt(meanSquaredDifference)
    }

    private fun copyOf(input: Representation): Representation =
        Representation.from(input.map { embedding -> Embedding.from(embedding.toDoubleArray()) })

    private fun output(value: Double, confidence: Double): Representation =
        Representation.of(Embedding.of(value, confidence))
}
