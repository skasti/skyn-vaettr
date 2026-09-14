package no.skasti.skynvaettr.expectations

import java.time.Instant
import no.skasti.skynvaettr.signals.Signal

/**
 * A persistent belief about the value of a [signal].
 *
 * An expectation is created by an external expectation gate after deciding that a model prediction
 * is worth believing. Unlike a [Prediction], an expectation is not a frozen snapshot of one model
 * output: compatible later predictions may cause the gate to refine [value] and [confidence] while
 * preserving the same expectation and its original [formedAt].
 *
 * [cost] expresses how expensive the commitment is, while [reward] expresses how valuable
 * fulfillment would be. Compatibility, refinement, reinforcement, superseding, lifecycle,
 * fulfillment and learning policy remain outside this data type.
 */
data class Expectation<T>(
    val signal: Signal<T>,
    val value: T,
    val formedAt: Instant,
    val cost: Double,
    val reward: Double,
    val confidence: Double,
) {
    init {
        require(cost.isFinite() && cost >= 0.0) {
            "Expectation cost must be finite and non-negative"
        }
        require(reward.isFinite() && reward >= 0.0) {
            "Expectation reward must be finite and non-negative"
        }
        require(confidence.isFinite() && confidence in 0.0..1.0) {
            "Expectation confidence must be finite and between 0 and 1"
        }
    }
}
