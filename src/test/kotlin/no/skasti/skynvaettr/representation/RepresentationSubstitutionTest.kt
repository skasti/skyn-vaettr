package no.skasti.skynvaettr.representation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import no.skasti.skynvaettr.attention.QueryNode
import no.skasti.skynvaettr.attention.ScaledDotProductAttention
import no.skasti.skynvaettr.runtime.SingleSlotPort
import no.skasti.skynvaettr.topology.Port
import no.skasti.skynvaettr.training.LearnableProcessingChain
import no.skasti.skynvaettr.training.LinearForwardPass
import no.skasti.skynvaettr.training.LinearParameters

class RepresentationSubstitutionTest {
    /** Minimal independent implementation; no NumericRepresentation adapter or storage. */
    private class AlternativeRepresentation(private val rows: List<Embedding>) : Representation {
        override val positions: Int get() = rows.size
        override val dimensions: Int get() = rows.first().dimensions
        override fun get(position: Int): Embedding = rows[position]
        override fun iterator(): Iterator<Embedding> = rows.iterator()
        override fun toList(): List<Embedding> = rows.toList()
    }

    @Test
    fun `ordinary interface ports and identity node preserve alternative object through fanout`() {
        val source: Port<Representation> = SingleSlotPort("source")
        val query = QueryNode()
        val first = SingleSlotPort<Representation>("first")
        val second = SingleSlotPort<Representation>("second")
        source.connectTo(query.input)
        query.output.connectTo(first, second)
        val alternative: Representation = AlternativeRepresentation(listOf(Embedding.of(2.0)))
        source.emit(alternative)
        assertSame(alternative, first.pending)
        assertSame(alternative, second.pending)
        assertNull(query.input.pending)
        first.clear()
        second.clear()
        val numeric: Representation = NumericRepresentation.of(Embedding.of(3.0))
        source.emit(numeric)
        assertSame(numeric, first.pending)
        assertSame(numeric, second.pending)
    }

    @Test
    fun `numeric attention accepts alternative inputs through the logical API`() {
        val queries = AlternativeRepresentation(listOf(Embedding.of(1.0)))
        val keys = AlternativeRepresentation(listOf(Embedding.of(2.0)))
        val values = AlternativeRepresentation(listOf(Embedding.of(7.0, -3.0)))
        val result = ScaledDotProductAttention().apply(queries, keys, values)
        assertEquals(listOf(Embedding.of(7.0, -3.0)), result.output.toList())
        assertIs<NumericRepresentation>(result.output)
    }

    @Test
    fun `numeric learning chain accepts alternative input and retains it in forward pass`() {
        val identity = LinearParameters.identity(1)
        val chain = LearnableProcessingChain(identity, identity, identity, identity)
        val output = SingleSlotPort<Representation>("output")
        chain.output.connectTo(output)
        val recorded = SingleSlotPort<LinearForwardPass>("recorded")
        chain.query.forwardPasses.connectTo(recorded)
        val alternative: Representation = AlternativeRepresentation(listOf(Embedding.of(2.0)))
        chain.input.receive(alternative)
        assertSame(alternative, recorded.pending?.input)
        assertEquals(listOf(Embedding.of(2.0)), output.pending?.toList())
        val pass = requireNotNull(recorded.pending)
        val gradient = AlternativeRepresentation(listOf(Embedding.of(1.0)))
        val backward = pass.backward(gradient)
        assertEquals(1.0, backward.input[0][0])
        assertEquals(2.0, backward.weights[0][0])
        assertEquals(1.0, backward.bias[0])
    }
}
