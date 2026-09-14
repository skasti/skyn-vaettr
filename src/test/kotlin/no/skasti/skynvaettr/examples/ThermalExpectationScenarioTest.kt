package no.skasti.skynvaettr.examples

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import no.skasti.skynvaettr.Vaettr

class ThermalExpectationScenarioTest {
    @Test
    fun `vaettr can accumulate a simple thermal world as learning experience`() {
        val vaettr = Vaettr()
        val world = ThermalExpectationScenario()

        world.simulate(
            duration = Duration.ofDays(3),
            step = Duration.ofMinutes(5),
        ) { samples ->
            vaettr.sense(samples)
        }

        assertEquals(
            setOf(world.outdoorTemperature.id, world.indoorTemperature.id),
            vaettr.sampleStore.signalIds(),
        )

        val samples = vaettr.sampleStore.get(Instant.EPOCH, Instant.EPOCH.plus(Duration.ofDays(3)))
        val outdoorValues = samples.filter { it.signal == world.outdoorTemperature }.map { it.value as Double }
        val indoorValues = samples.filter { it.signal == world.indoorTemperature }.map { it.value as Double }

        assertTrue(outdoorValues.min() >= 10.0 - 1e-9)
        assertTrue(outdoorValues.max() <= 25.0 + 1e-9)
        assertTrue(indoorValues.zipWithNext().all { (a, b) -> kotlin.math.abs(b - a) < 1.0 })
    }
}
