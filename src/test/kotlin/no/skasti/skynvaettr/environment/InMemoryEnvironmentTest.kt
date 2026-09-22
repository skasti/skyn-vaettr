package no.skasti.skynvaettr.environment

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.Signal

class InMemoryEnvironmentTest {
    @Test
    fun `appended samples are available through the read only sample store`() {
        val environment = InMemoryEnvironment()
        val sample = Sample(Signal<Double>("temperature"), 20.0, Instant.EPOCH)

        environment.append(sample)

        assertEquals(listOf(sample), environment.sampleStore.get(Instant.MIN, Instant.MAX))
        assertEquals(
            listOf(sample),
            environment.sampleStore.get(Instant.EPOCH, Instant.EPOCH.plusSeconds(1)),
        )
    }

    @Test
    fun `new input receives a monotonic processing sequence`() {
        val environment = InMemoryEnvironment()
        environment.append(listOf(1, "one"))

        val first = environment.getNew(listOf(Int::class, String::class))
        assertEquals(1uL, first?.sequence)
        assertEquals(listOf(1), first?.values?.get(Int::class))
        assertEquals(listOf("one"), first?.values?.get(String::class))

        environment.append(2)
        val second = environment.getNew(listOf(Int::class, String::class))
        assertEquals(2uL, second?.sequence)
        assertEquals(listOf(2), second?.values?.get(Int::class))
        assertEquals(emptyList(), second?.values?.get(String::class))
    }

    @Test
    fun `empty input does not create a processing input`() {
        val environment = InMemoryEnvironment()

        assertNull(environment.getNew(listOf(Int::class)))
        assertEquals(emptyList(), environment.getHistory(listOf(Int::class), 1uL, 10uL))
    }

    @Test
    fun `history returns projected processing inputs in a half open sequence range`() {
        val environment = InMemoryEnvironment()
        environment.append(1)
        environment.getNew(listOf(Int::class, String::class))
        environment.append("two")
        environment.getNew(listOf(Int::class, String::class))
        environment.append(3)
        environment.getNew(listOf(Int::class, String::class))

        val history = environment.getHistory(listOf(Int::class), 2uL, 4uL)

        assertEquals(listOf(3uL), history.map { it.sequence })
        assertEquals(listOf(3), history[0].values[Int::class])
        assertEquals(setOf(Int::class), history[0].values.keys)

        val allTypes = environment.getHistory(listOf(Int::class, String::class), 2uL, 4uL)
        assertEquals(listOf(2uL, 3uL), allTypes.map { it.sequence })
        assertEquals(listOf("two"), allTypes[0].values[String::class])
    }
}
