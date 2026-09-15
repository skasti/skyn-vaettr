package no.skasti.skynvaettr.runtime

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import no.skasti.skynvaettr.representation.SignalIdentityEmbedder
import no.skasti.skynvaettr.signals.InMemorySampleStore
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.Signal

class SensoryRepresentationEncoderTest {
    @Test
    fun `transition event encodes delta and event marker without target information`() {
        val encoder = SensoryRepresentationEncoder(InMemorySampleStore())
        val dimmer = Signal<Double>("state.kitchen.dimmer")
        val previous = Sample(dimmer, 0.2, Instant.EPOCH)
        val current = Sample(dimmer, 0.8, Instant.EPOCH.plusSeconds(10))

        val event = encoder.encodeTransitionEvent(previous, current, current.timestamp)
        val valueDimension = SignalIdentityEmbedder().dimensions

        assertEquals(0.6, event[valueDimension], absoluteTolerance = 1e-12)
        assertEquals(0.0, event[valueDimension + 1], absoluteTolerance = 1e-12)
        assertEquals(1.0, event[valueDimension + 2], absoluteTolerance = 1e-12)
    }
}
