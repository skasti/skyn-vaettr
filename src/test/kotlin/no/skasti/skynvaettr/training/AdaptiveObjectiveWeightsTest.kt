package no.skasti.skynvaettr.training

import kotlin.test.Test
import kotlin.test.assertTrue
import no.skasti.skynvaettr.signals.SignalId

class AdaptiveObjectiveWeightsTest {
    @Test
    fun `favours objective that beats persistence more consistently`() {
        val signal = SignalId("state.light")
        val weights = AdaptiveObjectiveWeights(smoothing = 1.0)

        weights.record(signal, LearningObjective.Continuous, prediction = 0.2, baseline = 0.2, observed = 0.8)
        weights.record(signal, LearningObjective.Transition, prediction = 0.75, baseline = 0.2, observed = 0.8)

        assertTrue(
            weights.weight(signal, LearningObjective.Transition) >
                weights.weight(signal, LearningObjective.Continuous),
        )
    }

    @Test
    fun `plateau does not count as evidence for continuous objective`() {
        val signal = SignalId("state.light")
        val weights = AdaptiveObjectiveWeights(smoothing = 1.0)

        weights.record(signal, LearningObjective.Continuous, prediction = 0.2, baseline = 0.2, observed = 0.2)

        assertTrue(weights.skill(signal, LearningObjective.Continuous) == null)
    }
}
