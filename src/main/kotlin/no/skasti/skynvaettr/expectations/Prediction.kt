package no.skasti.skynvaettr.expectations

import java.time.Instant
import no.skasti.skynvaettr.signals.Signal

/**
 * A model-produced prediction for one signal value.
 *
 * [confidence] expresses how strongly the model currently supports this prediction. It is deliberately
 * separate from expectation policy: an expectation gate may use confidence when deciding whether a
 * prediction is worth committing to, but the prediction itself does not encode that decision.
 *
 * Prediction deliberately has no mandatory target timestamp or fixed forecast horizon. Temporal
 * semantics belong to the model that produced it.
 */
data class Prediction<T>(
    val signal: Signal<T>,
    val value: T,
    val confidence: Double,
    val createdAt: Instant,
) {
    init {
        require(confidence.isFinite()) { "Prediction confidence must be finite" }
        require(confidence in 0.0..1.0) { "Prediction confidence must be between 0 and 1" }
    }
}
