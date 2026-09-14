package no.skasti.skynvaettr.attention

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation

class ScaledDotProductAttentionTest {
    @Test
    fun `attention rows are normalized`() {
        val result =
            ScaledDotProductAttention().selfAttention(
                Representation.of(
                    Embedding.of(1.0, 0.0),
                    Embedding.of(0.0, 1.0),
                ),
            )

        assertEquals(2, result.output.positions)
        assertEquals(2, result.weights.size)
        result.weights.forEach { row ->
            assertEquals(2, row.size)
            assertTrue(abs(row.sum() - 1.0) < 1e-12)
            assertTrue(row.all { it in 0.0..1.0 })
        }
    }

    @Test
    fun `matching query and key receive more weight`() {
        val result =
            ScaledDotProductAttention().apply(
                queries = Representation.of(Embedding.of(1.0, 0.0)),
                keys = Representation.of(Embedding.of(1.0, 0.0), Embedding.of(0.0, 1.0)),
                values = Representation.of(Embedding.of(10.0), Embedding.of(-10.0)),
            )

        assertTrue(result.weights.single()[0] > result.weights.single()[1])
        assertTrue(result.output[0][0] > 0.0)
    }

    @Test
    fun `value dimensions may differ from query and key dimensions`() {
        val result =
            ScaledDotProductAttention().apply(
                queries = Representation.of(Embedding.of(1.0, 0.0)),
                keys = Representation.of(Embedding.of(1.0, 0.0)),
                values = Representation.of(Embedding.of(1.0, 2.0, 3.0)),
            )

        assertEquals(3, result.output.dimensions)
        assertEquals(Embedding.of(1.0, 2.0, 3.0), result.output[0])
    }
}
