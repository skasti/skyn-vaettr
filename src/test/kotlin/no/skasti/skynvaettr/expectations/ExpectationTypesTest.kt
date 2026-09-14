package no.skasti.skynvaettr.expectations

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import no.skasti.skynvaettr.signals.Signal

class ExpectationTypesTest {
    private val signal = Signal<Double>("sensor.temperature")
    private val t0 = Instant.parse("2026-09-13T10:00:00Z")

    @Test
    fun `prediction carries bounded model confidence`() {
        val prediction = Prediction(signal, 21.0, 0.7)

        assertEquals(signal, prediction.signal)
        assertEquals(21.0, prediction.value)
        assertEquals(0.7, prediction.confidence)

        assertFailsWith<IllegalArgumentException> { Prediction(signal, 21.0, -0.01) }
        assertFailsWith<IllegalArgumentException> { Prediction(signal, 21.0, 1.01) }
        assertFailsWith<IllegalArgumentException> { Prediction(signal, 21.0, Double.NaN) }
    }

    @Test
    fun `expectation represents a persistent belief independently of a prediction snapshot`() {
        val expectation = Expectation(
            signal = signal,
            value = 21.2,
            formedAt = t0,
            confidence = 0.8,
        )

        assertEquals(signal, expectation.signal)
        assertEquals(21.2, expectation.value)
        assertEquals(t0, expectation.formedAt)
        assertEquals(0.8, expectation.confidence)
    }

    @Test
    fun `expectation validates confidence`() {
        assertFailsWith<IllegalArgumentException> {
            Expectation(signal, 21.0, t0, confidence = -0.01)
        }
        assertFailsWith<IllegalArgumentException> {
            Expectation(signal, 21.0, t0, confidence = 1.01)
        }
        assertFailsWith<IllegalArgumentException> {
            Expectation(signal, 21.0, t0, confidence = Double.NaN)
        }
    }

    @Test
    fun `expectation can be refined without changing when it was formed`() {
        val initial = Expectation(
            signal = signal,
            value = 21.0,
            formedAt = t0,
            confidence = 0.6,
        )

        val refined = initial.copy(value = 21.2, confidence = 0.8)

        assertEquals(21.2, refined.value)
        assertEquals(0.8, refined.confidence)
        assertEquals(t0, refined.formedAt)
    }
}
