package no.skasti.skynvaettr.examples

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KitchenLightScenarioTest {
    @Test
    fun `dimmer leads light and generic learning discovers both signals`() {
        val learning = KitchenLightLearning()
        val world = learning.world

        world.simulate(
            duration = Duration.ofDays(4),
            step = Duration.ofMinutes(5),
        ) { samples -> learning.vaettr.sense(samples) }

        assertEquals(
            setOf(world.dimmer.id, world.light.id),
            learning.sampleStore.signalIds(),
        )
        assertEquals(
            setOf(world.dimmer, world.light),
            learning.graph.predictionExecutions().map { it.prediction.signal }.toSet(),
        )
        assertEquals(2, learning.graph.models().size)
        assertTrue(learning.trainer.expectations.isNotEmpty())
        assertTrue(learning.trainer.experiences.isNotEmpty())

        val all = learning.sampleStore.get(Instant.EPOCH, Instant.EPOCH.plus(Duration.ofDays(4)).plusNanos(1))
        val dimmer = all.filter { it.signal == world.dimmer }.associate { it.timestamp to (it.value as Double) }
        val light = all.filter { it.signal == world.light }.associate { it.timestamp to (it.value as Double) }

        val delay = Duration.ofMinutes(10)
        val comparable = dimmer.entries.filter { (timestamp, _) -> timestamp.plus(delay) in light }
        assertTrue(comparable.isNotEmpty())
        assertTrue(comparable.all { (timestamp, value) -> light[timestamp.plus(delay)] == value })

        // Night is deterministic; daytime schedules are deliberately different across days.
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
