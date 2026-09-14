package no.skasti.skynvaettr.examples

import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import no.skasti.skynvaettr.models.OnlineKnnModel

class ThermalExpectationScenarioTest {
    @Test
    fun `thermal learner discovers prediction targets and bootstraps expectations`() {
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

        // Neither temperature signal is configured as a prediction target. The graph discovers one
        // independent model/decoder route for each observed Double-valued signal, and each model sees
        // the same complete sensory Representation so cross-signal relationships remain learnable.
        assertEquals(2, learning.graph.models().size)
        assertTrue(learning.graph.models().all { it is OnlineKnnModel })
        assertEquals(
            setOf(world.outdoorTemperature, world.indoorTemperature),
            learning.graph.predictionExecutions().map { it.prediction.signal }.toSet(),
        )

        assertTrue(learning.trainer.expectations.isNotEmpty())
        assertTrue(learning.trainer.experiences.isNotEmpty())
        assertTrue(
            learning.graph.models()
                .filterIsInstance<OnlineKnnModel>()
                .sumOf { it.trainingExampleCount } > 0,
        )
    }
}
