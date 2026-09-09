package no.skasti.skynvaettr.episodes

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import no.skasti.skynvaettr.signals.Signal
import no.skasti.skynvaettr.signals.SignalId

class EpisodeDataTest {
    private val indoor = Signal<Double>(
        SignalId("sensor.indoor.temperature"),
        mapOf("device_class" to "temperature", "unit" to "°C"),
    )
    private val outdoor = Signal<Double>(
        SignalId("sensor.outdoor.temperature"),
        mapOf("device_class" to "temperature", "unit" to "°C"),
    )

    private val from = Instant.parse("2026-09-09T10:00:00Z")
    private val to = Instant.parse("2026-09-09T10:10:00Z")

    @Test
    fun `definition keeps signal metadata available without storing it per value`() {
        val definition = EpisodeDefinition(from, to, listOf(indoor, outdoor))
        val data = EpisodeData(
            definition = definition,
            timestamps = listOf(from, from.plusSeconds(60)),
            values = listOf(
                listOf(21.5, 9.0),
                listOf(21.7, 9.2),
            ),
        )

        assertEquals("temperature", data.definition.signals[0].metadata["device_class"])
        assertEquals(2, data.rowCount)
        assertEquals(2, data.columnCount)
        assertEquals(21.7, data.values[1][0])
    }

    @Test
    fun `definition rejects an invalid time range`() {
        assertFailsWith<IllegalArgumentException> {
            EpisodeDefinition(to, from, listOf(indoor))
        }
    }

    @Test
    fun `definition rejects duplicate signals`() {
        assertFailsWith<IllegalArgumentException> {
            EpisodeDefinition(from, to, listOf(indoor, indoor))
        }
    }

    @Test
    fun `episode data requires one row per timestamp`() {
        val definition = EpisodeDefinition(from, to, listOf(indoor))

        assertFailsWith<IllegalArgumentException> {
            EpisodeData(
                definition,
                timestamps = listOf(from),
                values = emptyList(),
            )
        }
    }

    @Test
    fun `episode data requires one column per signal`() {
        val definition = EpisodeDefinition(from, to, listOf(indoor, outdoor))

        assertFailsWith<IllegalArgumentException> {
            EpisodeData(
                definition,
                timestamps = listOf(from),
                values = listOf(listOf(21.5)),
            )
        }
    }

    @Test
    fun `episode data rejects timestamps outside the definition`() {
        val definition = EpisodeDefinition(from, to, listOf(indoor))

        assertFailsWith<IllegalArgumentException> {
            EpisodeData(
                definition,
                timestamps = listOf(from.minusSeconds(1)),
                values = listOf(listOf(21.5)),
            )
        }
    }

    @Test
    fun `episode data requires ordered timestamps`() {
        val definition = EpisodeDefinition(from, to, listOf(indoor))

        assertFailsWith<IllegalArgumentException> {
            EpisodeData(
                definition,
                timestamps = listOf(to, from),
                values = listOf(listOf(21.7), listOf(21.5)),
            )
        }
    }
}
