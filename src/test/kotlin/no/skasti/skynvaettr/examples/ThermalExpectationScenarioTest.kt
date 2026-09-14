package no.skasti.skynvaettr.examples

import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ThermalExpectationScenarioTest {
    @Test
    fun `vaettr turns thermal expectations into replayable learning experience`() {
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

        val experiences = learning.trainer.experiences
        assertTrue(experiences.isNotEmpty())
        assertTrue(learning.model.trainingExampleCount > 0)

        experiences.forEach { experience ->
            val expectation = experience.expectation
            val result = assertNotNull(expectation.result)

            // Stability expectations are disabled for this example, so every resolved expectation
            // must represent a material directional prediction rather than "stay where you are".
            assertTrue(abs(expectation.expectationInitialValue - expectation.signalInitialValue) > 1e-12)

            assertEquals(expectation.formedAt, experience.episode.from)
            assertEquals(result.timestamp.plusNanos(1), experience.episode.to)
            assertEquals(
                listOf(world.outdoorTemperature, world.indoorTemperature),
                experience.episode.signals,
            )
        }
    }
}
