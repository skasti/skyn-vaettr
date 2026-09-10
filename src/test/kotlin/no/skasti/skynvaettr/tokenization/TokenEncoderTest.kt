package no.skasti.skynvaettr.tokenization

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TokenEncoderTest {
    private val encoder = DeterministicByteTokenEncoder()

    @Test
    fun `encodes lexical tokens to fixed width vectors`() {
        val vector = encoder.encode(Token("temperature"))

        assertEquals(encoder.dimensions, vector.size)
        assertEquals(64, vector.size)
    }

    @Test
    fun `encoding is deterministic`() {
        assertContentEquals(
            encoder.encode(Token("sensor")),
            encoder.encode(Token("sensor")),
        )
    }

    @Test
    fun `previously unseen token values do not share one unknown representation`() {
        val first = encoder.encode(Token("goblin"))
        val second = encoder.encode(Token("dragon"))

        assertFalse(first.contentEquals(second))
    }

    @Test
    fun `returned vectors are independent allocations`() {
        val first = encoder.encode(Token("temperature"))
        val second = encoder.encode(Token("temperature"))

        first[0] += 1.0

        assertFalse(first.contentEquals(second))
        assertContentEquals(encoder.encode(Token("temperature")), second)
    }

    @Test
    fun `feature sign is independent of bucket parity`() {
        val vector = encoder.encode(Token("a"))

        assertTrue(
            vector.withIndex().any { (index, value) -> index % 2 != 0 && value > 0.0 },
            "expected at least one positive contribution in an odd bucket",
        )
    }

    @Test
    fun `non empty token remains unit normalized after projection cancellation`() {
        val vector = DeterministicByteTokenEncoder(dimensions = 1).encode(Token("abab"))
        val norm = sqrt(vector.sumOf { it * it })

        assertTrue(abs(norm - 1.0) < 1e-12)
        assertTrue(vector.any { it != 0.0 })
    }

    @Test
    fun `non empty token vectors are unit normalized`() {
        val vector = encoder.encode(Token("temperature"))
        val norm = sqrt(vector.sumOf { it * it })

        assertTrue(abs(norm - 1.0) < 1e-12)
    }

    @Test
    fun `validates configuration`() {
        assertFailsWithIllegalArgument { DeterministicByteTokenEncoder(dimensions = 0) }
        assertFailsWithIllegalArgument { DeterministicByteTokenEncoder(projectionsPerFeature = 0) }
    }

    private fun assertFailsWithIllegalArgument(block: () -> Unit) {
        var failed = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }
}
