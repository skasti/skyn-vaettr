package no.skasti.skynvaettr.models

import kotlin.math.exp
import kotlin.math.sqrt
import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation

/**
 * Small dependency-free online model used as the first expectation-learning baseline.
 *
 * The model consumes the same [Representation] produced by the processing graph as inference and
 * training. Variable-length representations are mean-pooled across positions before nearest-neighbor
 * lookup, while the per-position embedding width must remain compatible with stored experience.
 * Its latent output has one position: [predicted numeric value, confidence]. A decoder assigns that
 * output domain meaning.
 */
class OnlineKnnModel(
    private val neighbours: Int = 8,
    private val maxExamples: Int = 512,
    private val confidenceExamples: Int = 12,
) : TrainableModel {
    private data class Example(
        val input: DoubleArray,
        val target: Double,
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
        val vector = pool(input)
        if (examples.isEmpty()) {
            return output(value = 0.0, confidence = 0.0)
        }

        val nearest = examples
            .map { example -> example to squaredDistance(vector, example.input) }
            .sortedBy { it.second }
            .take(neighbours)

        var weightedTarget = 0.0
        var weightTotal = 0.0
        nearest.forEach { (example, distanceSquared) ->
            val weight = 1.0 / (sqrt(distanceSquared) + 1e-6)
            weightedTarget += example.target * weight
            weightTotal += weight
        }

        val nearestDistance = sqrt(nearest.first().second)
        val experienceConfidence = (examples.size.toDouble() / confidenceExamples).coerceIn(0.0, 1.0)
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

        val vector = pool(input)
        val targetValue = target[0][0]
        val copies = weight.coerceAtMost(4.0).toInt().coerceAtLeast(1)
        repeat(copies) {
            examples.addLast(Example(vector.copyOf(), targetValue))
            while (examples.size > maxExamples) examples.removeFirst()
        }
    }

    private fun pool(input: Representation): DoubleArray =
        DoubleArray(input.dimensions) { dimension ->
            input.sumOf { embedding -> embedding[dimension] } / input.positions.toDouble()
        }

    private fun output(value: Double, confidence: Double): Representation =
        Representation.of(Embedding.of(value, confidence))

    private fun squaredDistance(left: DoubleArray, right: DoubleArray): Double =
        left.indices.sumOf { index ->
            val delta = left[index] - right[index]
            delta * delta
        }
}
