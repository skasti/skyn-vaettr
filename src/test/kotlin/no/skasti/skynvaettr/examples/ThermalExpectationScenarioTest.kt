package no.skasti.skynvaettr.examples

import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThermalExpectationScenarioTest {
    @Test
    fun `thermal learner does not bootstrap without stability expectations`() {
        val learning = ThermalExpectationLearning()
        val world = learning.world

        world.simulate(
            duration = Duration.ofDays(3),
            step = Duration.ofMinutes(5),
        ) { samples ->
            learning.vaettr.sense(samples)
        }

        assertEquals(
            setOf(world.outdoorTemperature.id, world.indoorTemperature.id),
            learning.sampleStore.signalIds(),
        )

        val samples = learning.sampleStore.get(Instant.EPOCH, Instant.EPOCH.plus(Duration.ofDays(3)))
        val outdoorValues = samples.filter { it.signal == world.outdoorTemperature }.map { it.value as Double }
        val indoorValues = samples.filter { it.signal == world.indoorTemperature }.map { it.value as Double }

        assertTrue(outdoorValues.min() >= 10.0 - 1e-9)
        assertTrue(outdoorValues.max() <= 25.0 + 1e-9)
        assertTrue(indoorValues.zipWithNext().all { (a, b) -> abs(b - a) < 1.0 })

        // With stability expectations disabled the unseeded KNN starts at confidence 0.0.
        // Those predictions are deliberately rejected, so no expectation can resolve into a
        // training experience and the learner cannot bootstrap itself from this loop alone.
        assertTrue(learning.trainer.expectations.isEmpty())
        assertTrue(learning.trainer.experiences.isEmpty())
        assertEquals(0, learning.model.trainingExampleCount)
        assertEquals(null, learning.trainer.active)
    }
}
