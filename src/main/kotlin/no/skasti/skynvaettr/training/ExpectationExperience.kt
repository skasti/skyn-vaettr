package no.skasti.skynvaettr.training

import no.skasti.skynvaettr.episodes.EpisodeDefinition
import no.skasti.skynvaettr.expectations.Expectation
import no.skasti.skynvaettr.models.PredictionDecoder
import no.skasti.skynvaettr.models.TrainableModel
import no.skasti.skynvaettr.representation.Representation

/**
 * One resolved expectation together with the latent model context that produced it.
 *
 * [priority] is intentionally generic. A lifecycle policy may interpret it as surprise, cost,
 * utility, prediction miss, or another replay signal without changing the episode model.
 */
data class ExpectationExperience<T>(
    val episode: EpisodeDefinition,
    val expectation: Expectation<T>,
    val model: TrainableModel,
    val decoder: PredictionDecoder<T>,
    val input: Representation,
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
