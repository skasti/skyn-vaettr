package no.skasti.skynvaettr.expectations

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import no.skasti.skynvaettr.signals.Signal

class ExpectationTypesTest {
    private val signal = Signal<Double>("sensor.temperature")
    private val otherSignal = Signal<Double>("sensor.other_temperature")
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
    fun `expectation starts from prediction confidence`() {
        val prediction = Prediction(signal, 21.0, 0.6)
        val expectation = Expectation(
            prediction = prediction,
            formedAt = t0,
            cost = 0.2,
            reward = 1.0,
        )

        assertEquals(0.6, expectation.confidence)
        assertEquals(0.2, expectation.cost)
        assertEquals(1.0, expectation.reward)
    }

    @Test
    fun `expectation validates commitment values`() {
        val prediction = Prediction(signal, 21.0, 0.6)

        assertFailsWith<IllegalArgumentException> {
            Expectation(prediction, t0, cost = -0.1, reward = 1.0)
        }
        assertFailsWith<IllegalArgumentException> {
            Expectation(prediction, t0, cost = 0.2, reward = -0.1)
        }
        assertFailsWith<IllegalArgumentException> {
            Expectation(prediction, t0, cost = 0.2, reward = 1.0, confidence = 0.5)
        }
    }

    @Test
    fun `matching prediction can reinforce confidence`() {
        val initial = Prediction(signal, 21.0, 0.6)
        val expectation = Expectation(initial, t0, cost = 0.2, reward = 1.0)
        val reinforced = expectation.reinforcedBy(Prediction(signal, 21.0, 0.85))

        assertEquals(0.85, reinforced.confidence)
        assertEquals(t0, reinforced.formedAt)
        assertEquals(0.2, reinforced.cost)
        assertEquals(1.0, reinforced.reward)
    }

    @Test
    fun `reinforcement never lowers confidence`() {
        val initial = Prediction(signal, 21.0, 0.8)
        val expectation = Expectation(initial, t0, cost = 0.2, reward = 1.0)
        val reinforced = expectation.reinforcedBy(Prediction(signal, 21.0, 0.7))

        assertEquals(0.8, reinforced.confidence)
    }

    @Test
    fun `reinforcement must represent the same prediction`() {
        val initial = Prediction(signal, 21.0, 0.6)
        val expectation = Expectation(initial, t0, cost = 0.2, reward = 1.0)

        assertFailsWith<IllegalArgumentException> {
            expectation.reinforcedBy(Prediction(otherSignal, 21.0, 0.8))
        }
        assertFailsWith<IllegalArgumentException> {
            expectation.reinforcedBy(Prediction(signal, 22.0, 0.8))
        }
    }
}
