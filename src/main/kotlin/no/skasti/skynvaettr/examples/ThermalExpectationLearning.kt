package no.skasti.skynvaettr.examples

import no.skasti.skynvaettr.Vaettr
import no.skasti.skynvaettr.models.LearnedAttentionTransitionModel
import no.skasti.skynvaettr.models.TransitionPredictionDecoder
import no.skasti.skynvaettr.representation.SignalIdentityEmbedder
import no.skasti.skynvaettr.runtime.SensingProcessingGraph
import no.skasti.skynvaettr.signals.InMemorySampleStore
import no.skasti.skynvaettr.training.TransitionPredictionTrainer

/** Prediction-only wiring for [ThermalExpectationScenario]. Expectations are intentionally not used. */
class ThermalExpectationLearning(
    val world: ThermalExpectationScenario = ThermalExpectationScenario(),
) {
    val sampleStore = InMemorySampleStore()
    val graph = SensingProcessingGraph(sampleStore = sampleStore)
    private val signalEmbedder = SignalIdentityEmbedder()
    val decoder = TransitionPredictionDecoder(signalEmbedder)
    val model = LearnedAttentionTransitionModel(
        signalEmbeddingDimensions = signalEmbedder.dimensions,
        seed = 17,
    )
    val predictionTrainer = TransitionPredictionTrainer(model, decoder)
    val vaettr = Vaettr(
        sampleStore = sampleStore,
        graph = graph,
        trainers = listOf(predictionTrainer),
    )
}
