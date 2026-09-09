package no.skasti.skynvaettr.episodes

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.SampleStore
import no.skasti.skynvaettr.signals.Signal
import no.skasti.skynvaettr.signals.SignalId

class SampleStoreEpisodeExtensionsTest {
    private val indoor = Signal<Double>("sensor.indoor.temperature")
    private val outdoor = Signal<Double>("sensor.outdoor.temperature")
    private val from = Instant.parse("2026-09-09T10:00:00Z")
    private val middle = Instant.parse("2026-09-09T10:01:00Z")
    private val to = Instant.parse("2026-09-09T10:02:00Z")

    @Test
    fun `get episode materializes samples in episode signal order`() {
        val store = RecordingSampleStore(
            listOf(
                Sample(outdoor, 9.0, from),
                Sample(indoor, 21.5, from),
                Sample(indoor, 21.4, middle),
                Sample(outdoor, 8.9, middle),
            ),
        )
        val episode = EpisodeDefinition(from, to, listOf(indoor, outdoor))

        val data = store.get(episode)

        assertEquals(listOf(indoor.id, outdoor.id), store.requestedSignals)
        assertEquals(from, store.after)
        assertEquals(to, store.before)
        assertEquals(listOf(from, middle), data.timestamps)
        assertEquals(
            listOf(
                listOf(21.5, 9.0),
                listOf(21.4, 8.9),
            ),
            data.values,
        )
    }

    @Test
    fun `get episode rejects missing signals at a timestamp`() {
        val store = RecordingSampleStore(
            listOf(
                Sample(indoor, 21.5, from),
                Sample(outdoor, 9.0, from),
                Sample(indoor, 21.4, middle),
            ),
        )
        val episode = EpisodeDefinition(from, to, listOf(indoor, outdoor))

        assertFailsWith<IllegalArgumentException> {
            store.get(episode)
        }
    }

    @Test
    fun `get episode rejects duplicate samples for the same signal and timestamp`() {
        val store = RecordingSampleStore(
            listOf(
                Sample(indoor, 21.5, from),
                Sample(indoor, 21.6, from),
                Sample(outdoor, 9.0, from),
            ),
        )
        val episode = EpisodeDefinition(from, to, listOf(indoor, outdoor))

        assertFailsWith<IllegalArgumentException> {
            store.get(episode)
        }
    }

    private class RecordingSampleStore(
        private val samples: List<Sample<*>>,
    ) : SampleStore {
        var after: Instant? = null
        var before: Instant? = null
        var requestedSignals: List<SignalId> = emptyList()

        override fun get(
            after: Instant,
            before: Instant,
            signals: Collection<SignalId>,
        ): List<Sample<*>> {
            this.after = after
            this.before = before
            requestedSignals = signals.toList()
            return samples
        }
    }
}
