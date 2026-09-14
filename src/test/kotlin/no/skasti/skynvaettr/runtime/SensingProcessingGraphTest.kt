package no.skasti.skynvaettr.runtime

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import no.skasti.skynvaettr.representation.SignalIdentityEmbedder
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.Signal

class SensingProcessingGraphTest {
    @Test
    fun `builds generic temporal representation from signal identity value and relative time`() {
        val graph =
            SensingProcessingGraph(
                historyAges = listOf(Duration.ofSeconds(10), Duration.ZERO),
            )
        val signal = Signal<Double>("sensor.indoor.temperature")

        graph.sense(Sample(signal, 20.0, Instant.EPOCH))
        graph.sense(Sample(signal, 21.0, Instant.EPOCH.plusSeconds(10)))

        val representation = assertNotNull(graph.latestRepresentation)
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
    fun `boolean samples use numeric zero one representation`() {
        val graph =
            SensingProcessingGraph(
                historyAges = listOf(Duration.ofSeconds(5), Duration.ZERO),
            )
        val signal = Signal<Boolean>("state.indoor.light")

        graph.sense(Sample(signal, true, Instant.EPOCH))

        val representation = assertNotNull(graph.latestRepresentation)
        val valueDimension = representation.dimensions - 2
        assertEquals(1.0, representation[0][valueDimension])
    }
}
