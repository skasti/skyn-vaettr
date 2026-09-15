package no.skasti.skynvaettr.expectations

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.Signal

class NumericExpectationPolicyTest {
    private val signal = Signal<Double>("sensor.temperature")

    @Test
    fun `low-confidence prediction does not bootstrap an expectation`() {
        val policy = NumericExpectationPolicy(createStabilityExpectations = false)
        val previous = Sample(signal, 20.0, Instant.parse("2026-01-01T00:00:00Z"))
        val current = Sample(signal, 20.1, Instant.parse("2026-01-01T00:05:00Z"))
        val prediction = Prediction(signal, value = 21.0, confidence = 0.0)

        assertNull(policy.open(prediction, previous, current))
    }

    @Test
    fun `confident material prediction becomes expectation`() {
        val policy = NumericExpectationPolicy(createStabilityExpectations = false)
        val current = Sample(signal, 20.0, Instant.parse("2026-01-01T00:00:00Z"))
        val prediction = Prediction(signal, value = 21.0, confidence = 0.8)

        val expectation = assertNotNull(policy.open(prediction, previous = null, current = current))

        assertEquals(20.0, expectation.signalInitialValue)
        assertEquals(21.0, expectation.value)
        assertEquals(0.8, expectation.confidence)
    }
}
