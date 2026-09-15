package no.skasti.skynvaettr.models

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln1p
import kotlin.math.sqrt
import no.skasti.skynvaettr.expectations.Prediction
import no.skasti.skynvaettr.representation.Embedder
import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.representation.SignalIdentityEmbedder
import no.skasti.skynvaettr.signals.Signal
import no.skasti.skynvaettr.signals.SignalId

/**
 * Decodes dynamically ranked next-transition candidates.
 *
 * Each output position represents one candidate signal and has layout
 * `[candidate signal identity..., next-signal logit, encoded value, experience confidence]`.
 * The model learns the candidate score; the decoder only maps the winning candidate identity back
 * to a concrete observed signal and decodes its numeric value.
 */
class TransitionPredictionDecoder(
    private val signalEmbedder: Embedder<SignalId> = SignalIdentityEmbedder(),
) {
    private val signals = linkedMapOf<SignalId, Signal<Double>>()

    val signalEmbeddingDimensions: Int
        get() = signalEmbedder.dimensions

    val outputDimensions: Int
        get() = signalEmbeddingDimensions + 3

    val trainingTargetDimensions: Int
        get() = signalEmbeddingDimensions + 1

    fun observe(signal: Signal<Double>) {
        signals.putIfAbsent(signal.id, signal)
    }

    fun knownSignals(): List<Signal<Double>> = signals.values.toList()

    fun candidateSignals(output: Representation): List<Signal<Double>> {
        validateOutput(output)
        return output.map { embedding -> decodeIdentity(embedding) }
    }

    fun decode(output: Representation): Prediction<Double> {
        validateOutput(output)
        val candidates = candidateSignals(output)
        val logits = DoubleArray(output.positions) { index -> output[index][signalEmbeddingDimensions] }
        val probabilities = softmax(logits)
        val winner = logits.indices.maxBy { logits[it] }
        val embedding = output[winner]
        val experienceConfidence = embedding[signalEmbeddingDimensions + 2].coerceIn(0.0, 1.0)

        return Prediction(
            signal = candidates[winner],
            value = decodeValue(embedding[signalEmbeddingDimensions + 1]),
            confidence = (probabilities[winner] * experienceConfidence).coerceIn(0.0, 1.0),
        )
    }

    fun trainingTarget(
        signal: Signal<Double>,
        value: Double,
    ): Representation {
        require(value.isFinite()) { "transition target value must be finite" }
        observe(signal)
        val identity = signalEmbedder.embed(signal.id).toDoubleArray()
        return Representation.of(Embedding.from(identity + encodeValue(value)))
    }

    internal fun encodeValue(value: Double): Double =
        if (value == 0.0) 0.0 else kotlin.math.sign(value) * ln1p(abs(value))

    internal fun decodeValue(value: Double): Double =
        if (value == 0.0) 0.0 else kotlin.math.sign(value) * kotlin.math.expm1(abs(value))

    private fun validateOutput(output: Representation) {
        require(output.positions > 0) { "transition prediction output must contain at least one candidate" }
        require(output.dimensions == outputDimensions) {
            "expected $outputDimensions transition output dimensions, got ${output.dimensions}"
        }
        require(signals.isNotEmpty()) { "cannot decode a transition before observing numeric signals" }
    }

    private fun decodeIdentity(embedding: Embedding): Signal<Double> {
        val identity = DoubleArray(signalEmbeddingDimensions) { embedding[it] }
        return signals.values.minBy { candidate ->
            identityDistance(identity, signalEmbedder.embed(candidate.id).toDoubleArray())
        }
    }

    private fun softmax(scores: DoubleArray): DoubleArray {
        val maxScore = scores.maxOrNull() ?: error("softmax requires candidate scores")
        val exponentials = DoubleArray(scores.size) { index -> exp(scores[index] - maxScore) }
        val total = exponentials.sum()
        return DoubleArray(scores.size) { index -> exponentials[index] / total }
    }

    private fun identityDistance(
        left: DoubleArray,
        right: DoubleArray,
    ): Double {
        require(left.size == right.size)
        return sqrt(left.indices.sumOf { index ->
            val delta = left[index] - right[index]
            delta * delta
        } / left.size.toDouble())
    }
}
