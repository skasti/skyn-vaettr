package no.skasti.skynvaettr.expectations

import java.time.Instant

/**
 * Describes why and when an expectation stopped being active.
 *
 * [value] is deliberately open-ended. Core records the lifecycle outcome without defining a fixed
 * vocabulary such as fulfilled, violated, abandoned or superseded; the subsystem managing the
 * expectation owns those semantics.
 */
data class ExpectationResult(
    val value: String,
    val timestamp: Instant,
) {
    init {
        require(value.isNotBlank()) { "Expectation result value must not be blank" }
    }
}
