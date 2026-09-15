package no.skasti.skynvaettr.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.representation.SignalIdentityEmbedder
import no.skasti.skynvaettr.signals.Signal

class TransitionPredictionDecoderTest {
    @Test
    fun `decodes highest-scoring candidate signal`() {
        val embedder = SignalIdentityEmbedder()
        val decoder = TransitionPredictionDecoder(embedder)
        val dimmer = Signal<Double>("state.kitchen.dimmer")
        val light = Signal<Double>("state.kitchen.light")
        decoder.observe(dimmer)
        decoder.observe(light)

        val dimmerIdentity = embedder.embed(dimmer.id).toDoubleArray()
        val lightIdentity = embedder.embed(light.id).toDoubleArray()
        val output = Representation.from(
            listOf(
                Embedding.from(dimmerIdentity + doubleArrayOf(-1.0, decoder.encodeValue(0.2), 1.0)),
                Embedding.from(lightIdentity + doubleArrayOf(2.0, decoder.encodeValue(0.75), 1.0)),
            ),
        )

        val prediction = decoder.decode(output)

        assertEquals(light.id, prediction.signal.id)
        assertEquals(0.75, prediction.value, absoluteTolerance = 1e-9)
        assertTrue(prediction.confidence > 0.9)
    }
}
