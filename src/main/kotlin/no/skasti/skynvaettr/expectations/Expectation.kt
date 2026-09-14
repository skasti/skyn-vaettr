package no.skasti.skynvaettr.expectations

import java.time.Instant
import no.skasti.skynvaettr.signals.Signal

/**
 * A persistent belief about the value of a [signal].
 *
 * An expectation is created by an external expectation gate after deciding that a model prediction
 * is worth believing. Unlike a [Prediction], an expectation is not a frozen snapshot of one model
 * output: compatible later predictions may cause the gate to refine [value] and [confidence] while
 * preserving the same expectation and its original reference values.
 *
 * [signalInitialValue] is the observed value of [signal] when the expectation was formed.
 * [expectationInitialValue] is the value initially expected when the belief was formed and defaults
 * to [value]. Both initial values are intended to remain stable while [value] may be refined.
 *
 * [result] is null while the expectation is active. Once set, it records the open-ended lifecycle
 * outcome and the time at which the expectation stopped being active. Core deliberately does not
 * define a fixed vocabulary for result values.
 *
 * Compatibility, refinement, reinforcement, superseding, lifecycle policy, utility/cost calculation,
 * fulfillment and learning policy remain outside this data type.
 */
data class Expectation<T>(
    val signal: Signal<T>,
    val signalInitialValue: T,
    val value: T,
    val expectationInitialValue: T = value,
    val formedAt: Instant,
    val confidence: Double,
    val result: ExpectationResult? = null,
) {
    init {
        require(confidence.isFinite() && confidence in 0.0..1.0) {
            "Expectation confidence must be finite and between 0 and 1"
        }
        require(result == null || !result.timestamp.isBefore(formedAt)) {
            "Expectation result cannot predate expectation formation"
        }
    }
}
