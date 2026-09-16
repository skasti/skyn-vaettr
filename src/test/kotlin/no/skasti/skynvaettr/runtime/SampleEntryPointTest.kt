package no.skasti.skynvaettr.runtime

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import no.skasti.skynvaettr.representation.SignalIdentityEmbedder
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.Signal

class SampleEntryPointTest {
    @Test
    fun `builds generic temporal representation from canonical sample history`() {
        val entryPoint =
            SampleEntryPoint(
                historyAges = listOf(Duration.ofSeconds(10), Duration.ZERO),
            )
        val signal = Signal<Double>("sensor.indoor.temperature")
        val first = Sample(signal, 20.0, Instant.EPOCH)
        val second = Sample(signal, 21.0, Instant.EPOCH.plusSeconds(10))

        entryPoint.process(listOf(first))
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
        val entryPoint =
            SampleEntryPoint(
                historyAges = listOf(Duration.ofSeconds(10), Duration.ZERO),
            )
        val signal = Signal<Double>("sensor.indoor.temperature")
        val historical = Sample(signal, 20.0, Instant.EPOCH.plusSeconds(3))
        val current = Sample(signal, 21.0, Instant.EPOCH.plusSeconds(10))

        entryPoint.process(listOf(historical, current))

        val representation = assertNotNull(entryPoint.latestRepresentation)
        val timeDimension = representation.dimensions - 1
        assertEquals(-0.7, representation[0][timeDimension], absoluteTolerance = 1e-12)
        assertEquals(0.0, representation[1][timeDimension])
    }

    @Test
    fun `boolean samples use numeric zero one representation`() {
        val entryPoint =
            SampleEntryPoint(
                historyAges = listOf(Duration.ofSeconds(5), Duration.ZERO),
            )
        val signal = Signal<Boolean>("state.indoor.light")
        val sample = Sample(signal, true, Instant.EPOCH)

        entryPoint.process(listOf(sample))

        val representation = assertNotNull(entryPoint.latestRepresentation)
        val valueDimension = representation.dimensions - 2
        assertEquals(1.0, representation[0][valueDimension])
    }
}
