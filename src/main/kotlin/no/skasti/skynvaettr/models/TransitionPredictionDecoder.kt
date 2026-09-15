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
 * Decodes a latent next-transition output without binding the model to one target signal up front.
 *
 * Output layout is `[signal identity embedding..., encoded value, confidence]`. The signal portion is
 * matched against the identities of numeric signals observed so far. This keeps target-signal
 * selection in learned representation space while the decoder remains responsible only for mapping
 * that latent identity back to a concrete [Signal].
 */
class TransitionPredictionDecoder(
    private val signalEmbedder: Embedder<SignalId> = SignalIdentityEmbedder(),
) {
    private val signals = linkedMapOf<SignalId, Signal<Double>>()

    val signalEmbeddingDimensions: Int
        get() = signalEmbedder.dimensions

    val outputDimensions: Int
        get() = signalEmbeddingDimensions + 2

    val trainingTargetDimensions: Int
        get() = signalEmbeddingDimensions + 1

    fun observe(signal: Signal<Double>) {
        signals.putIfAbsent(signal.id, signal)
    }

    fun knownSignals(): List<Signal<Double>> = signals.values.toList()

    fun decode(output: Representation): Prediction<Double> {
        require(output.positions == 1) { "transition prediction output must contain one position" }
        require(output.dimensions == outputDimensions) {
            "expected $outputDimensions transition output dimensions, got ${output.dimensions}"
        }
        require(signals.isNotEmpty()) { "cannot decode a transition before observing numeric signals" }

        val embedding = output[0]
        val predictedIdentity = DoubleArray(signalEmbeddingDimensions) { embedding[it] }
        val signal = signals.values.minBy { candidate ->
            identityDistance(predictedIdentity, signalEmbedder.embed(candidate.id).toDoubleArray())
        }
        val identityDistance = identityDistance(
            predictedIdentity,
            signalEmbedder.embed(signal.id).toDoubleArray(),
        )
        val modelConfidence = embedding[signalEmbeddingDimensions + 1].coerceIn(0.0, 1.0)
        val identityConfidence = exp(-identityDistance).coerceIn(0.0, 1.0)

        return Prediction(
            signal = signal,
            value = decodeValue(embedding[signalEmbeddingDimensions]),
            confidence = (modelConfidence * identityConfidence).coerceIn(0.0, 1.0),
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
