package no.skasti.skynvaettr.examples

import no.skasti.skynvaettr.Vaettr
import no.skasti.skynvaettr.expectations.NumericExpectationPolicy
import no.skasti.skynvaettr.models.DoublePredictionRouteFactory
import no.skasti.skynvaettr.runtime.SensingProcessingGraph
import no.skasti.skynvaettr.signals.InMemorySampleStore
import no.skasti.skynvaettr.training.AdaptiveObjectiveWeights
import no.skasti.skynvaettr.training.ExpectationExperience
import no.skasti.skynvaettr.training.ExpectationTrainer
import no.skasti.skynvaettr.training.ObservationTrainer
import no.skasti.skynvaettr.training.TransitionTrainer
import no.skasti.skynvaettr.training.WeightedPriorityReplaySelector

/**
 * Example wiring for [ThermalExpectationScenario].
 *
 * Samples enter the same generic sensing graph used by the runtime. The graph builds a
 * [no.skasti.skynvaettr.representation.Representation] and discovers prediction routes from the
 * observed signal types. Continuous and transition objectives train in parallel and adapt their
 * relative contribution from predictive usefulness; expectations remain a separate belief layer.
 */
class ThermalExpectationLearning(
    val world: ThermalExpectationScenario = ThermalExpectationScenario(),
) {
    val sampleStore = InMemorySampleStore()
    val graph = SensingProcessingGraph(
        sampleStore = sampleStore,
        predictionRouteFactories = listOf(DoublePredictionRouteFactory()),
    )
    val objectiveWeights = AdaptiveObjectiveWeights()
    val observationTrainer = ObservationTrainer(objectiveWeights)
    val transitionTrainer = TransitionTrainer(objectiveWeights)
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
        trainers = listOf(observationTrainer, transitionTrainer, trainer),
    )
}
