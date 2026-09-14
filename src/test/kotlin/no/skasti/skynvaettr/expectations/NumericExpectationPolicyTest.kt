package no.skasti.skynvaettr.expectations

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.Signal

class NumericExpectationPolicyTest {
    private val signal = Signal<Double>("sensor.temperature")

    @Test
    fun `low-confidence prediction can bootstrap directional expectation from observed movement`() {
        val policy = NumericExpectationPolicy(createStabilityExpectations = false)
        val previous = Sample(signal, 20.0, Instant.parse("2026-01-01T00:00:00Z"))
        val current = Sample(signal, 20.1, Instant.parse("2026-01-01T00:05:00Z"))
        val prediction = Prediction(signal, value = 0.0, confidence = 0.0)

        val expectation = assertNotNull(policy.open(prediction, previous, current))

        assertEquals(current.value, expectation.signalInitialValue)
        assertTrue(expectation.value > current.value)
        assertEquals(0.05, expectation.confidence)
    }

    @Test
    fun `bootstrap does not invent direction without a preceding observation`() {
        val policy = NumericExpectationPolicy(createStabilityExpectations = false)
        val current = Sample(signal, 20.0, Instant.parse("2026-01-01T00:00:00Z"))
        val prediction = Prediction(signal, value = 0.0, confidence = 0.0)

        assertNull(policy.open(prediction, previous = null, current = current))
    }
}
