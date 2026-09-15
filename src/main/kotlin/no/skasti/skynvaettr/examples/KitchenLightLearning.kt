package no.skasti.skynvaettr.examples

import no.skasti.skynvaettr.Vaettr
import no.skasti.skynvaettr.models.LearnedAttentionTransitionModel
import no.skasti.skynvaettr.models.TransitionPredictionDecoder
import no.skasti.skynvaettr.representation.SignalIdentityEmbedder
import no.skasti.skynvaettr.runtime.TransitionPredictionProcessingGraph
import no.skasti.skynvaettr.signals.InMemorySampleStore
import no.skasti.skynvaettr.training.TransitionPredictionTrainer

/** Generic prediction-only wiring for [KitchenLightScenario]. Expectations are intentionally not used. */
class KitchenLightLearning(
    val world: KitchenLightScenario = KitchenLightScenario(),
) {
    val sampleStore = InMemorySampleStore()
    private val signalEmbedder = SignalIdentityEmbedder()
    val decoder = TransitionPredictionDecoder(signalEmbedder)
    val model = LearnedAttentionTransitionModel(
        signalEmbeddingDimensions = signalEmbedder.dimensions,
        seed = 31,
    )
    val graph = TransitionPredictionProcessingGraph(
        sampleStore = sampleStore,
        model = model,
        decoder = decoder,
    )
    val predictionTrainer = TransitionPredictionTrainer()
    val vaettr = Vaettr(
        sampleStore = sampleStore,
        graph = graph,
        trainers = listOf(predictionTrainer),
    )
}
