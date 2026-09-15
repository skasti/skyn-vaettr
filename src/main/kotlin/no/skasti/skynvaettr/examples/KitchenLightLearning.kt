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

/** Generic multi-objective observation- and expectation-learning wiring for [KitchenLightScenario]. */
class KitchenLightLearning(
    val world: KitchenLightScenario = KitchenLightScenario(),
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
            seed = 31,
        ),
    )
    val vaettr = Vaettr(
        sampleStore = sampleStore,
        graph = graph,
        trainers = listOf(observationTrainer, transitionTrainer, trainer),
    )
}
