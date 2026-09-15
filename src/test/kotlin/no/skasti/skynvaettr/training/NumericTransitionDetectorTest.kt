package no.skasti.skynvaettr.training

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.Signal

class NumericTransitionDetectorTest {
    @Test
    fun `accumulates gradual movement from last transition anchor`() {
        val signal = Signal<Double>("sensor.temperature")
        val detector = NumericTransitionDetector(rangeFraction = 0.05, rangeFloor = 1.0)

        assertNull(detector.observe(Sample(signal, 20.00, Instant.EPOCH)))
        assertNull(detector.observe(Sample(signal, 20.02, Instant.EPOCH.plusSeconds(60))))
        assertNull(detector.observe(Sample(signal, 20.04, Instant.EPOCH.plusSeconds(120))))
        assertNotNull(detector.observe(Sample(signal, 20.06, Instant.EPOCH.plusSeconds(180))))
    }

    @Test
    fun `detects discrete level change immediately`() {
        val signal = Signal<Double>("state.dimmer")
        val detector = NumericTransitionDetector(rangeFraction = 0.05, rangeFloor = 1.0)

        assertNull(detector.observe(Sample(signal, 0.2, Instant.EPOCH)))
        assertNotNull(detector.observe(Sample(signal, 0.8, Instant.EPOCH.plusSeconds(60))))
    }
}
