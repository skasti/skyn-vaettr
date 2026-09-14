package no.skasti.skynvaettr.models

import kotlin.math.exp
import kotlin.math.sqrt
import no.skasti.skynvaettr.expectations.Prediction
import no.skasti.skynvaettr.signals.Signal

/**
 * Small dependency-free default model for numeric vector inputs.
 *
 * Training stores resolved examples. Prediction is the inverse-distance-weighted mean of the
 * nearest examples. This is intentionally modest: it provides a working online default while the
 * runtime keeps the [TrainablePredictionModel] boundary open for neural or other implementations.
 */
class OnlineKnnPredictionModel(
    private val signal: Signal<Double>,
    private val inputDimensions: Int,
    private val neighbours: Int = 8,
    private val maxExamples: Int = 512,
    private val confidenceExamples: Int = 12,
) : TrainablePredictionModel<DoubleArray, Double> {
    private data class Example(
        val input: DoubleArray,
        val target: Double,
    )

    private val examples = ArrayDeque<Example>()

    init {
        require(inputDimensions > 0) { "inputDimensions must be positive" }
        require(neighbours > 0) { "neighbours must be positive" }
        require(maxExamples > 0) { "maxExamples must be positive" }
        require(confidenceExamples > 0) { "confidenceExamples must be positive" }
    }

    val trainingExampleCount: Int
        get() = examples.size

    override fun predict(input: DoubleArray): Prediction<Double> {
        requireInput(input)
        if (examples.isEmpty()) {
            return Prediction(signal = signal, value = 0.0, confidence = 0.0)
        }

        val nearest = examples
            .map { example -> example to squaredDistance(input, example.input) }
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

        return Prediction(
            signal = signal,
            value = weightedTarget / weightTotal,
            confidence = (experienceConfidence * localityConfidence).coerceIn(0.0, 1.0),
        )
    }

    override fun train(
        input: DoubleArray,
        target: Double,
        weight: Double,
    ) {
        requireInput(input)
        require(target.isFinite()) { "target must be finite" }
        require(weight.isFinite() && weight >= 0.0) { "weight must be finite and non-negative" }
        if (weight == 0.0) return

        // Replayed examples are deliberately inserted again: repeated experience should influence
        // a local-memory baseline in the same direction that repeated SGD updates would.
        val copies = weight.coerceAtMost(4.0).toInt().coerceAtLeast(1)
        repeat(copies) {
            examples.addLast(Example(input.copyOf(), target))
            while (examples.size > maxExamples) examples.removeFirst()
        }
    }

    private fun requireInput(input: DoubleArray) {
        require(input.size == inputDimensions) {
            "Expected $inputDimensions input values, got ${input.size}"
        }
        require(input.all(Double::isFinite)) { "Prediction input values must be finite" }
    }

    private fun squaredDistance(
        left: DoubleArray,
        right: DoubleArray,
    ): Double = left.indices.sumOf { index ->
        val delta = left[index] - right[index]
        delta * delta
    }
}
