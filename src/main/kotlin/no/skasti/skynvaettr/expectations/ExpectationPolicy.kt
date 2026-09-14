package no.skasti.skynvaettr.expectations

import no.skasti.skynvaettr.signals.Sample

/** Result of evaluating one active expectation against a new observation. */
data class ExpectationAssessment<T>(
    val result: ExpectationResult,
    val observedValue: T,
    val priority: Double,
) {
    init {
        require(priority.isFinite() && priority >= 0.0) {
            "Expectation assessment priority must be finite and non-negative"
        }
    }
}

/**
 * Policy boundary between decoded predictions and persistent expectations.
 *
 * Implementations declare which decoded prediction values they understand, decide what is worth
 * committing to, and decide when an active expectation has accumulated enough evidence to resolve.
 * [open] may return null when the prediction is not worth committing to. The contract deliberately
 * has no fixed forecast horizon.
 */
interface ExpectationPolicy<T> {
    fun supports(prediction: Prediction<*>): Boolean

    fun open(
        prediction: Prediction<T>,
        current: Sample<T>,
    ): Expectation<T>?

    fun assess(
        expectation: Expectation<T>,
        previous: Sample<T>?,
        current: Sample<T>,
    ): ExpectationAssessment<T>?
}
