package no.skasti.skynvaettr

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import no.skasti.skynvaettr.runtime.ProcessingGraph
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.Signal

class VaettrTest {
    @Test
    fun `sense forwards observed samples into configured graph`() {
        val received = mutableListOf<List<Sample<*>>>()
        val vaettr = Vaettr(ProcessingGraph { received += it })
        val sample = Sample(Signal<Double>("sensor.indoor.temperature"), 21.5, Instant.EPOCH)

        vaettr.sense(sample)

        assertEquals(listOf(listOf(sample)), received)
    }

    @Test
    fun `empty sensory batches do not wake graph`() {
        var calls = 0
        val vaettr = Vaettr(ProcessingGraph { calls++ })

        vaettr.sense(emptyList())

        assertEquals(0, calls)
    }
}
