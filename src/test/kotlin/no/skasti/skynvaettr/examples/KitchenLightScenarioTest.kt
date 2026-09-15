package no.skasti.skynvaettr.examples

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KitchenLightScenarioTest {
    @Test
    fun `light follows dimmer after configured delay`() {
        val world = KitchenLightScenario()
        val samples = mutableListOf<no.skasti.skynvaettr.signals.Sample<*>>()

        world.simulate(
            duration = Duration.ofDays(4),
            step = Duration.ofMinutes(5),
            onSense = samples::addAll,
        )

        val dimmer = samples.filter { it.signal == world.dimmer }.associate { it.timestamp to (it.value as Double) }
        val light = samples.filter { it.signal == world.light }.associate { it.timestamp to (it.value as Double) }
        val delay = Duration.ofMinutes(10)
        val comparable = dimmer.entries.filter { (timestamp, _) -> timestamp.plus(delay) in light }

        assertTrue(comparable.isNotEmpty())
        assertTrue(comparable.all { (timestamp, value) -> light[timestamp.plus(delay)] == value })
    }

    @Test
    fun `days vary while nights stay off`() {
        val world = KitchenLightScenario()
        val samples = mutableListOf<no.skasti.skynvaettr.signals.Sample<*>>()

        world.simulate(
            duration = Duration.ofDays(4),
            step = Duration.ofMinutes(5),
            onSense = samples::addAll,
        )

        val dimmer = samples.filter { it.signal == world.dimmer }.associate { it.timestamp to (it.value as Double) }

        (0L until 4L).forEach { day ->
            val midnight = Instant.EPOCH.plus(Duration.ofDays(day))
            listOf(0L, 1L, 2L, 3L, 4L, 5L, 23L).forEach { hour ->
                assertEquals(0.0, dimmer[midnight.plus(Duration.ofHours(hour))])
            }
        }

        val daySignatures = (0L until 4L).map { day ->
            val start = Instant.EPOCH.plus(Duration.ofDays(day)).plus(Duration.ofHours(6))
            (0L..16L).mapNotNull { offset -> dimmer[start.plus(Duration.ofHours(offset))] }
        }
        assertTrue(daySignatures.distinct().size > 1)
    }
}
