package no.skasti.skynvaettr.examples

import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ThermalExpectationScenarioTest {
    @Test
    fun `thermal model bootstraps directional expectations through graph`() {
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

        // Inference uses the generic sensory Representation and the graph-owned model/decoder.
        assertSame(learning.model, learning.graph.models().single())
        val execution = learning.graph.predictionExecutions().single()
        assertSame(learning.model, execution.model)
        assertEquals(world.indoorTemperature, execution.prediction.signal)
        assertTrue(execution.input.positions > 0)

        // Stability expectations remain disabled. Low-confidence cold-start predictions are instead
        // bootstrapped from observed movement, which must create resolved directional experience and
        // eventually train the graph-owned model.
        assertTrue(learning.trainer.expectations.isNotEmpty())
        assertTrue(learning.trainer.experiences.isNotEmpty())
        assertTrue(learning.model.trainingExampleCount > 0)
        assertTrue(
            learning.trainer.expectations.all {
                abs(it.expectationInitialValue - it.signalInitialValue) > 1e-12
            },
        )
    }
}
