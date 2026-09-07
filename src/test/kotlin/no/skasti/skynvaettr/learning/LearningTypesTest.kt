package no.skasti.skynvaettr.learning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LearningTypesTest {
    @Test
    fun `sensory frames preserve arbitrary sensor names and values`() {
        val frame = SensoryFrame(mapOf("a" to 5.0, "b" to 15.0, "sun-dial" to -0.25))
        assertEquals(5.0, frame["a"])
        assertEquals(-0.25, frame["sun-dial"])
    }

    @Test
    fun `sensory frames defensively copy caller owned maps`() {
        val values = mutableMapOf("a" to 5.0)
        val frame = SensoryFrame(values)

        values["a"] = 99.0
        values["late"] = Double.NaN

        assertEquals(5.0, frame["a"])
        assertEquals(null, frame["late"])
    }

    @Test
    fun `normalized values reject out of range input`() {
        assertFailsWith<IllegalArgumentException> { EffectorValue(1.01) }
        assertFailsWith<IllegalArgumentException> { Consequence(-1.01) }
    }
}
