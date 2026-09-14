package no.skasti.skynvaettr

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import no.skasti.skynvaettr.runtime.ProcessingGraph
import no.skasti.skynvaettr.signals.InMemorySampleStore
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.Signal
import no.skasti.skynvaettr.training.Trainer

class VaettrTest {
    @Test
    fun `sense stores samples before notifying graph`() {
        val store = InMemorySampleStore()
        val sample = Sample(Signal<Double>("sensor.indoor.temperature"), 21.5, Instant.EPOCH)
        var graphSawStoredSample = false
        val graph = ProcessingGraph {
            graphSawStoredSample = store.latestAtOrBefore(sample.signal.id, sample.timestamp) == sample
        }
        val vaettr = Vaettr(sampleStore = store, graph = graph)

        vaettr.sense(sample)

        assertEquals(true, graphSawStoredSample)
    }

    @Test
    fun `sense forwards observed samples into configured graph`() {
        val received = mutableListOf<List<Sample<*>>>()
        val vaettr = Vaettr(ProcessingGraph { received += it })
        val sample = Sample(Signal<Double>("sensor.indoor.temperature"), 21.5, Instant.EPOCH)

        vaettr.sense(sample)

        assertEquals(listOf(listOf(sample)), received)
    }

    @Test
    fun `trainer is notified after graph completes the sense cycle`() {
        val order = mutableListOf<String>()
        val sample = Sample(Signal<Double>("sensor.indoor.temperature"), 21.5, Instant.EPOCH)
        val graph = ProcessingGraph { order += "graph" }
        val trainer = Trainer { received ->
            val expected: List<Sample<*>> = listOf(sample)
            assertEquals(expected, received)
            order += "trainer"
        }
        val vaettr = Vaettr(
            graph = graph,
            trainers = listOf(trainer),
        )

        vaettr.sense(sample)

        assertEquals(listOf("graph", "trainer"), order)
    }

    @Test
    fun `trainers can read samples already committed to store on sense completion`() {
        val store = InMemorySampleStore()
        val sample = Sample(Signal<Double>("sensor.indoor.temperature"), 21.5, Instant.EPOCH)
        var trainerSawStoredSample = false
        val trainer = Trainer {
            trainerSawStoredSample = store.latestAtOrBefore(sample.signal.id, sample.timestamp) == sample
        }
        val vaettr = Vaettr(
            sampleStore = store,
            graph = ProcessingGraph { },
            trainers = listOf(trainer),
        )

        vaettr.sense(sample)

        assertEquals(true, trainerSawStoredSample)
    }

    @Test
    fun `empty sensory batches do not wake graph`() {
        var calls = 0
        val vaettr = Vaettr(ProcessingGraph { calls++ })

        vaettr.sense(emptyList())

        assertEquals(0, calls)
    }

    @Test
    fun `default vaettr accepts numeric sensory input`() {
        val vaettr = Vaettr()

        vaettr.sense(
            Sample(
                Signal<Double>("sensor.indoor.temperature"),
                21.5,
                Instant.EPOCH,
            ),
        )
    }
}
