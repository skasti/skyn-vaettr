package no.skasti.skynvaettr.examples

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import no.skasti.skynvaettr.Vaettr
import no.skasti.skynvaettr.runtime.DefaultTopology
import no.skasti.skynvaettr.signals.InMemorySampleStore
import no.skasti.skynvaettr.signals.SampleEntryPoint

class ThermalExpectationScenarioTest {
    @Test
    fun `vaettr can accumulate a simple thermal world as learning experience`() {
        val world = ThermalExpectationScenario()
        val sampleStore = InMemorySampleStore()
        val sampleEntryPoint = SampleEntryPoint(sampleStore)
        val topology = DefaultTopology(world, sampleStore, listOf(sampleEntryPoint))
        val vaettr = Vaettr(world, topology)

        world.simulate(
            vaettr = vaettr,
            duration = Duration.ofDays(3),
            step = Duration.ofMinutes(5),
        )

        assertEquals(
            setOf(world.outdoorTemperature.id, world.indoorTemperature.id),
            topology.sampleStore.signalIds(),
        )

        val samples = topology.sampleStore.get(Instant.EPOCH, Instant.EPOCH.plus(Duration.ofDays(3)))
        val outdoorValues = samples.filter { it.signal == world.outdoorTemperature }.map { it.value as Double }
        val indoorValues = samples.filter { it.signal == world.indoorTemperature }.map { it.value as Double }

        assertTrue(outdoorValues.min() >= 10.0 - 1e-9)
        assertTrue(outdoorValues.max() <= 25.0 + 1e-9)
        assertTrue(indoorValues.zipWithNext().all { (a, b) -> kotlin.math.abs(b - a) < 1.0 })
    }
}
