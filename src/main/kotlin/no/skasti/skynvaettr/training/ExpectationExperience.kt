package no.skasti.skynvaettr.training

import no.skasti.skynvaettr.episodes.EpisodeDefinition
import no.skasti.skynvaettr.expectations.Expectation

/**
 * One resolved expectation together with the episode and model input that produced it.
 *
 * [priority] is intentionally generic. A lifecycle policy may interpret it as surprise, cost,
 * utility, prediction miss, or another replay signal without changing the episode model.
 */
data class ExpectationExperience<I, T>(
    val episode: EpisodeDefinition,
    val expectation: Expectation<T>,
    val input: I,
    val observedValue: T,
    val priority: Double,
) {
    init {
        require(expectation.result != null) { "Expectation experience requires a resolved expectation" }
        require(priority.isFinite() && priority >= 0.0) {
            "Experience priority must be finite and non-negative"
        }
    }
}
