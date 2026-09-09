package no.skasti.skynvaettr.signals

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class SignalTest {
    @Test
    fun `signal defines a uniquely identified typed value with metadata`() {
        val signal = Signal<Double>(
            id = SignalId("sensor.kontor_presence_temperature"),
            metadata = mapOf(
                "state_class" to "measurement",
                "unit_of_measurement" to "°C",
                "device_class" to "temperature",
                "friendly_name" to "Kontor Presence Temperature",
            ),
        )

        assertEquals(SignalId("sensor.kontor_presence_temperature"), signal.id)
        assertEquals("sensor.kontor_presence_temperature", signal.name)
        assertEquals("temperature", signal.metadata["device_class"])
    }

    @Test
    fun `signal metadata is copied from caller owned map`() {
        val metadata = mutableMapOf<String, Any>("unit" to "°C")
        val signal = Signal<Double>("temperature", metadata)

        metadata["unit"] = "K"

        assertEquals("°C", signal.metadata["unit"])
    }

    @Test
    fun `signal ids must not be blank`() {
        assertFailsWith<IllegalArgumentException> { SignalId(" ") }
        assertFailsWith<IllegalArgumentException> { Signal<Double>(" ") }
    }

    @Test
    fun `signal identity is its id rather than metadata`() {
        val a = Signal<Double>("temperature", mapOf("unit" to "°C"))
        val b = Signal<Double>("temperature", mapOf("unit" to "K"))
        val c = Signal<Double>("other-temperature", mapOf("unit" to "°C"))

        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun `sample binds a typed signal value to an explicit timestamp`() {
        val signal = Signal<Double>("sensor.kontor_presence_temperature")
        val timestamp = Instant.parse("2026-09-09T11:00:00Z")

        val sample = Sample(signal, 25.9, timestamp)

        assertEquals(signal, sample.signal)
        assertEquals(25.9, sample.value)
        assertEquals(timestamp, sample.timestamp)
    }
}
