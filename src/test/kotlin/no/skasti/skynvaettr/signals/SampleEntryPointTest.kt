package no.skasti.skynvaettr.signals

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import no.skasti.skynvaettr.representation.SignalIdentityEmbedder

class SampleEntryPointTest {
    @Test
    fun `builds generic temporal representation from canonical sample history`() {
        val sampleStore = InMemorySampleStore()
        val entryPoint =
            SampleEntryPoint(
                sampleStore = sampleStore,
                historyAges = listOf(Duration.ofSeconds(10), Duration.ZERO),
            )
        val signal = Signal<Double>("sensor.indoor.temperature")
        val first = Sample(signal, 20.0, Instant.EPOCH)
        val second = Sample(signal, 21.0, Instant.EPOCH.plusSeconds(10))

        sampleStore.append(first)
        entryPoint.process(listOf(first))
        sampleStore.append(second)
        entryPoint.process(listOf(second))

        val representation = assertNotNull(entryPoint.latestRepresentation)
        assertEquals(2, representation.positions)
        assertEquals(SignalIdentityEmbedder().dimensions + 2, representation.dimensions)

        val valueDimension = representation.dimensions - 2
        val timeDimension = representation.dimensions - 1
        assertEquals(20.0, representation[0][valueDimension])
        assertEquals(-1.0, representation[0][timeDimension])
        assertEquals(21.0, representation[1][valueDimension])
        assertEquals(0.0, representation[1][timeDimension])
    }

    @Test
    fun `event driven samples retain actual observation time instead of implied state time`() {
        val sampleStore = InMemorySampleStore()
        val entryPoint =
            SampleEntryPoint(
                sampleStore = sampleStore,
                historyAges = listOf(Duration.ofSeconds(10), Duration.ZERO),
            )
        val signal = Signal<Double>("sensor.indoor.temperature")
        val historical = Sample(signal, 20.0, Instant.EPOCH.plusSeconds(3))
        val current = Sample(signal, 21.0, Instant.EPOCH.plusSeconds(10))

        sampleStore.append(listOf(historical, current))
        entryPoint.process(listOf(historical, current))

        val representation = assertNotNull(entryPoint.latestRepresentation)
        val timeDimension = representation.dimensions - 1
        assertEquals(-0.7, representation[0][timeDimension], absoluteTolerance = 1e-12)
        assertEquals(0.0, representation[1][timeDimension])
    }

    @Test
    fun `boolean samples use numeric zero one representation`() {
        val sampleStore = InMemorySampleStore()
        val entryPoint =
            SampleEntryPoint(
                sampleStore = sampleStore,
                historyAges = listOf(Duration.ofSeconds(5), Duration.ZERO),
            )
        val signal = Signal<Boolean>("state.indoor.light")
        val sample = Sample(signal, true, Instant.EPOCH)

        sampleStore.append(sample)
        entryPoint.process(listOf(sample))

        val representation = assertNotNull(entryPoint.latestRepresentation)
        val valueDimension = representation.dimensions - 2
        assertEquals(1.0, representation[0][valueDimension])
    }

    @Test
    fun `preserves sub millisecond relative timing`() {
        val sampleStore = InMemorySampleStore()
        val entryPoint =
            SampleEntryPoint(
                sampleStore = sampleStore,
                historyAges = listOf(Duration.ofMillis(1), Duration.ZERO),
            )
        val signal = Signal<Double>("sensor.indoor.temperature")
        val current = Sample(signal, 21.0, Instant.EPOCH.plusSeconds(1))
        val historical = Sample(signal, 20.0, current.timestamp.minusNanos(500_000))

        sampleStore.append(listOf(historical, current))
        entryPoint.process(listOf(current))

        val representation = assertNotNull(entryPoint.latestRepresentation)
        val timeDimension = representation.dimensions - 1
        assertEquals(-0.5, representation[0][timeDimension], absoluteTolerance = 1e-12)
        assertEquals(0.0, representation[1][timeDimension])
    }
}
