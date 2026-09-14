package no.skasti.skynvaettr.representation

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import no.skasti.skynvaettr.signals.Signal

class SignalIdentityEmbedderTest {
    @Test
    fun `same signal identity produces stable embedding`() {
        val embedder = SignalIdentityEmbedder()
        val first = embedder.embed(Signal<Double>("sensor.indoor.temperature"))
        val second = embedder.embed(Signal<Double>("sensor.indoor.temperature"))

        assertEquals(embedder.dimensions, first.dimensions)
        assertContentEquals(first.toDoubleArray(), second.toDoubleArray())
    }

    @Test
    fun `different lexical signal identities remain distinguishable`() {
        val embedder = SignalIdentityEmbedder()
        val temperature = embedder.embed(Signal<Double>("sensor.indoor.temperature"))
        val light = embedder.embed(Signal<Boolean>("state.indoor.light"))

        assertFalse(temperature == light)
    }

    @Test
    fun `returned embedding does not expose mutable encoder storage`() {
        val embedder = SignalIdentityEmbedder()
        val embedding = embedder.embed(Signal<Double>("sensor.indoor.temperature"))
        val copy = embedding.toDoubleArray()
        val original = embedding[0]

        copy[0] = original + 1.0

        assertEquals(original, embedding[0])
    }
}
