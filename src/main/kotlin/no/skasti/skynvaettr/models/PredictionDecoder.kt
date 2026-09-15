package no.skasti.skynvaettr.models

import no.skasti.skynvaettr.expectations.Prediction
import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.signals.Signal

/**
 * Decodes latent model output into a concrete prediction understood by the runtime.
 *
 * The inverse [trainingTarget] mapping keeps training targets in the same latent output space used
 * by inference without teaching the model about signal/domain semantics.
 */
interface PredictionDecoder<T> {
    val signal: Signal<T>

    fun supports(output: Representation): Boolean = true

    fun decode(output: Representation): Prediction<T>

    fun trainingTarget(value: T): Representation
}

/**
 * Minimal numeric decoder used by the current thermal experiment.
 *
 * Position 0 encodes [predicted value, confidence]. Training targets only need the observed value;
 * confidence is derived by the model from its accumulated experience.
 */
class NumericPredictionDecoder(
    override val signal: Signal<Double>,
) : PredictionDecoder<Double> {
    override fun supports(output: Representation): Boolean =
        output.positions == 1 && output.dimensions >= 2

    override fun decode(output: Representation): Prediction<Double> {
        require(supports(output)) { "Numeric prediction output must be one position with at least two dimensions" }
        return Prediction(
            signal = signal,
            value = output[0][0],
            confidence = output[0][1].coerceIn(0.0, 1.0),
        )
    }

    override fun trainingTarget(value: Double): Representation {
        require(value.isFinite()) { "numeric training target must be finite" }
        return Representation.of(Embedding.of(value))
    }
}
