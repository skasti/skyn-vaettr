package no.skasti.skynvaettr.examples

import no.skasti.skynvaettr.Vaettr
import no.skasti.skynvaettr.expectations.NumericExpectationPolicy
import no.skasti.skynvaettr.models.DoublePredictionRouteFactory
import no.skasti.skynvaettr.runtime.SensingProcessingGraph
import no.skasti.skynvaettr.signals.InMemorySampleStore
import no.skasti.skynvaettr.training.ExpectationExperience
import no.skasti.skynvaettr.training.ExpectationTrainer
import no.skasti.skynvaettr.training.ObservationTrainer
import no.skasti.skynvaettr.training.WeightedPriorityReplaySelector

/**
 * Example wiring for [ThermalExpectationScenario].
 *
 * Samples enter the same generic sensing graph used by the runtime. The graph builds a
 * [no.skasti.skynvaettr.representation.Representation] and discovers prediction routes from the
 * observed signal types. Models learn continuously from ordinary observed transitions; expectations
 * are formed only from sufficiently confident predictions and provide additional replay/surprise.
 */
class ThermalExpectationLearning(
    val world: ThermalExpectationScenario = ThermalExpectationScenario(),
) {
    val sampleStore = InMemorySampleStore()
    val graph = SensingProcessingGraph(
        sampleStore = sampleStore,
        predictionRouteFactories = listOf(DoublePredictionRouteFactory()),
    )
    val observationTrainer = ObservationTrainer()
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
        trainers = listOf(observationTrainer, trainer),
    )
}
