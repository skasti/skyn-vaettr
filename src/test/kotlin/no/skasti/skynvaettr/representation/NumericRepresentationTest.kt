package no.skasti.skynvaettr.representation

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class NumericRepresentationTest {
    private val first = Embedding.of(1.0, 2.0)
    private val second = Embedding.of(3.0, 4.0)

    @Test
    fun `numeric factories implement the logical instance API`() {
        val concrete: NumericRepresentation = NumericRepresentation.of(first, second)
        val representations: List<Representation> = listOf(concrete, NumericRepresentation.from(listOf(first, second)))
        for (representation in representations) {
            assertEquals(2, representation.positions)
            assertEquals(2, representation.dimensions)
            assertSame(first, representation[0])
            assertSame(second, representation[1])
            assertEquals(listOf(first, second), representation.toList())
            assertEquals(listOf(first, second), representation.iterator().asSequence().toList())
            assertContentEquals(doubleArrayOf(1.0, 2.0), representation[0].toDoubleArray())
            assertFailsWith<IndexOutOfBoundsException> { representation[-1] }
            assertFailsWith<IndexOutOfBoundsException> { representation[2] }
        }
    }

    @Test
    fun `compatibility factories construct numeric values through the interface`() {
        val of: Representation = Representation.of(first, second)
        val from: Representation = Representation.from(listOf(first, second))
        assertIs<NumericRepresentation>(of)
        assertIs<NumericRepresentation>(from)
        assertEquals(of.toList(), from.toList())
        assertSame(first, of[0])
    }

    @Test
    fun `all factories preserve nonempty and consistent dimension validation`() {
        assertFailsWith<IllegalArgumentException> { NumericRepresentation.of() }
        assertFailsWith<IllegalArgumentException> { NumericRepresentation.from(emptyList()) }
        assertFailsWith<IllegalArgumentException> { Representation.of() }
        assertFailsWith<IllegalArgumentException> { Representation.from(emptyList()) }
        val incompatible = Embedding.of(5.0)
        assertFailsWith<IllegalArgumentException> { NumericRepresentation.of(first, incompatible) }
        assertFailsWith<IllegalArgumentException> { NumericRepresentation.from(listOf(first, incompatible)) }
        assertFailsWith<IllegalArgumentException> { Representation.of(first, incompatible) }
        assertFailsWith<IllegalArgumentException> { Representation.from(listOf(first, incompatible)) }
    }

    @Test
    fun `numeric representation copies input lists and vararg arrays`() {
        val rows = mutableListOf(first, second)
        val from = NumericRepresentation.from(rows)
        rows[0] = Embedding.of(9.0)
        rows.clear()
        val array = arrayOf(first, second)
        val of = NumericRepresentation.of(*array)
        array[0] = Embedding.of(9.0)
        assertEquals(listOf(first, second), from.toList())
        assertEquals(listOf(first, second), of.toList())
    }

    @Test
    fun `returned numeric lists cannot mutate representation storage`() {
        // Use multiple rows so Kotlin returns a mutable list copy.
        val representation = NumericRepresentation.of(first, second)
        val copy = representation.toList() as MutableList<Embedding>
        copy[0] = Embedding.of(9.0)
        copy.clear()
        assertEquals(listOf(first, second), representation.toList())
    }

    @Test
    fun `embedding arrays remain defensively copied and validated`() {
        val values = doubleArrayOf(1.0, 2.0)
        val embedding = Embedding.from(values)
        val representation = NumericRepresentation.of(embedding)
        values[0] = 99.0
        representation[0].toDoubleArray()[1] = 99.0
        assertContentEquals(doubleArrayOf(1.0, 2.0), representation[0].toDoubleArray())
        assertFailsWith<IllegalArgumentException> { Embedding.of() }
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { Embedding.of(invalid) }
        }
    }
}
