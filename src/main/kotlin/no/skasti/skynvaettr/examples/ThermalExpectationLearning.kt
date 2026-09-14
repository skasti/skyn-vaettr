package no.skasti.skynvaettr.examples

import no.skasti.skynvaettr.Vaettr
import no.skasti.skynvaettr.expectations.NumericExpectationPolicy
import no.skasti.skynvaettr.models.NumericPredictionDecoder
import no.skasti.skynvaettr.models.OnlineKnnModel
import no.skasti.skynvaettr.runtime.SensingProcessingGraph
import no.skasti.skynvaettr.signals.InMemorySampleStore
import no.skasti.skynvaettr.training.ExpectationExperience
import no.skasti.skynvaettr.training.ExpectationTrainer
import no.skasti.skynvaettr.training.WeightedPriorityReplaySelector

/**
 * Example wiring for [ThermalExpectationScenario].
 *
 * Samples enter the same generic sensing graph used by the runtime. The graph builds a
 * [no.skasti.skynvaettr.representation.Representation], runs compatible models, and decodes their
 * latent output into predictions. The trainer discovers those executions from the graph; it is not
 * wired to a target signal, model instance, feature vector, or explicit episode signal set.
 */
class ThermalExpectationLearning(
    val world: ThermalExpectationScenario = ThermalExpectationScenario(),
) {
    val sampleStore = InMemorySampleStore()
    val model = OnlineKnnModel()
    val decoder = NumericPredictionDecoder(world.indoorTemperature)
    val graph = SensingProcessingGraph(
        sampleStore = sampleStore,
        models = listOf(model),
        predictionDecoders = listOf(decoder),
    )
    val trainer = ExpectationTrainer(
        sampleStore = sampleStore,
        policy = NumericExpectationPolicy(
            createStabilityExpectations = false,
        ),
        replaySelector = WeightedPriorityReplaySelector<ExpectationExperience<Double>>(
            priority = { it.priority },
            seed = 17,
        ),
    )
    val vaettr = Vaettr(
        sampleStore = sampleStore,
        graph = graph,
        trainers = listOf(trainer),
    )
}
