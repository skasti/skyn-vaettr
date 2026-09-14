package no.skasti.skynvaettr.runtime

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import no.skasti.skynvaettr.models.DoublePredictionRouteFactory
import no.skasti.skynvaettr.models.OnlineKnnModel
import no.skasti.skynvaettr.representation.SignalIdentityEmbedder
import no.skasti.skynvaettr.signals.InMemorySampleStore
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.Signal

class SensingProcessingGraphTest {
    @Test
    fun `builds generic temporal representation from canonical sample history`() {
        val store = InMemorySampleStore()
        val graph =
            SensingProcessingGraph(
                sampleStore = store,
                historyAges = listOf(Duration.ofSeconds(10), Duration.ZERO),
            )
        val signal = Signal<Double>("sensor.indoor.temperature")
        val first = Sample(signal, 20.0, Instant.EPOCH)
        val second = Sample(signal, 21.0, Instant.EPOCH.plusSeconds(10))

        store.append(first)
        graph.sense(listOf(first))
        store.append(second)
        graph.sense(listOf(second))

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
    fun `event driven samples retain actual observation time instead of implied state time`() {
        val store = InMemorySampleStore()
        val graph =
            SensingProcessingGraph(
                sampleStore = store,
                historyAges = listOf(Duration.ofSeconds(10), Duration.ZERO),
            )
        val signal = Signal<Double>("sensor.indoor.temperature")
        val historical = Sample(signal, 20.0, Instant.EPOCH.plusSeconds(3))
        val current = Sample(signal, 21.0, Instant.EPOCH.plusSeconds(10))

        store.append(listOf(historical, current))
        graph.sense(listOf(current))

        val representation = assertNotNull(graph.latestRepresentation)
        val timeDimension = representation.dimensions - 1
        assertEquals(-0.7, representation[0][timeDimension], absoluteTolerance = 1e-12)
        assertEquals(0.0, representation[1][timeDimension])
    }

    @Test
    fun `boolean samples use numeric zero one representation`() {
        val store = InMemorySampleStore()
        val graph =
            SensingProcessingGraph(
                sampleStore = store,
                historyAges = listOf(Duration.ofSeconds(5), Duration.ZERO),
            )
        val signal = Signal<Boolean>("state.indoor.light")
        val sample = Sample(signal, true, Instant.EPOCH)

        store.append(sample)
        graph.sense(listOf(sample))

        val representation = assertNotNull(graph.latestRepresentation)
        val valueDimension = representation.dimensions - 2
        assertEquals(1.0, representation[0][valueDimension])
    }

    @Test
    fun `discovers independent prediction routes for observed double signals`() {
        val store = InMemorySampleStore()
        val graph = SensingProcessingGraph(
            sampleStore = store,
            historyAges = listOf(Duration.ofSeconds(5), Duration.ZERO),
            predictionRouteFactories = listOf(DoublePredictionRouteFactory()),
        )
        val firstSignal = Signal<Double>("sensor.alpha")
        val secondSignal = Signal<Double>("sensor.beta")
        val samples = listOf(
            Sample(firstSignal, 1.0, Instant.EPOCH),
            Sample(secondSignal, 2.0, Instant.EPOCH),
        )

        store.append(samples)
        graph.sense(samples)

        val models = graph.models()
        assertEquals(2, models.size)
        assertEquals(true, models.all { it is OnlineKnnModel })
        assertNotSame(models[0], models[1])
        assertEquals(
            setOf(firstSignal, secondSignal),
            graph.predictionExecutions().map { it.prediction.signal }.toSet(),
        )
        assertEquals(2, graph.predictionExecutions().map { it.input }.distinctBy { it.toList() }.size.coerceAtMost(1) + 1)
    }
}
