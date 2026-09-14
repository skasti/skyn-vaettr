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
 * [open] may inspect the preceding observation to support horizon-free bootstrap/exploration and may
 * return null when there is not enough evidence to commit. The contract deliberately has no fixed
 * forecast horizon.
 */
interface ExpectationPolicy<T> {
    fun supports(prediction: Prediction<*>): Boolean

    fun open(
        prediction: Prediction<T>,
        previous: Sample<T>?,
        current: Sample<T>,
    ): Expectation<T>?

    fun assess(
        expectation: Expectation<T>,
        previous: Sample<T>?,
        current: Sample<T>,
    ): ExpectationAssessment<T>?
}
