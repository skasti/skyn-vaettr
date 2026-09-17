package no.skasti.skynvaettr.attention

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.runtime.SingleSlotPort

class ProjectionNodeTest {
    @Test
    fun `projection nodes have descriptive default names`() {
        assertEquals("Query", QueryNode().name)
        assertEquals("Key", KeyNode().name)
        assertEquals("Value", ValueNode().name)
        assertEquals("Temperature query", QueryNode(name = "Temperature query").name)
    }

    @Test
    fun `sample representation can fan out through query key and value nodes`() {
        val sample = Representation.of(Embedding.of(1.0, 2.0))
        val source = SingleSlotPort<Representation>("samples")
        val query = QueryNode()
        val key = KeyNode()
        val value = ValueNode()
        val qSink = SingleSlotPort<Representation>("q-sink")
        val kSink = SingleSlotPort<Representation>("k-sink")
        val vSink = SingleSlotPort<Representation>("v-sink")

        source.connectTo(query.input)
        source.connectTo(key.input)
        source.connectTo(value.input)
        query.output.connectTo(qSink)
        key.output.connectTo(kSink)
        value.output.connectTo(vSink)

        source.emit(sample)

        assertSame(sample, qSink.pending)
        assertSame(sample, kSink.pending)
        assertSame(sample, vSink.pending)
        assertNull(query.input.pending)
        assertNull(key.input.pending)
        assertNull(value.input.pending)
    }

    @Test
    fun `linear query key and value projections can use different output widths`() {
        val input = Representation.of(Embedding.of(2.0, 3.0), Embedding.of(-1.0, 4.0))
        val query = QueryNode(LinearRepresentationProjection(listOf(doubleArrayOf(1.0, 0.0))))
        val key = KeyNode(LinearRepresentationProjection(listOf(doubleArrayOf(0.0, 1.0))))
        val value = ValueNode(
            LinearRepresentationProjection(
                weights = listOf(doubleArrayOf(1.0, 1.0), doubleArrayOf(2.0, -1.0)),
                bias = doubleArrayOf(0.5, -0.5),
            ),
        )
        val qSink = SingleSlotPort<Representation>("q-sink")
        val kSink = SingleSlotPort<Representation>("k-sink")
        val vSink = SingleSlotPort<Representation>("v-sink")
        query.output.connectTo(qSink)
        key.output.connectTo(kSink)
        value.output.connectTo(vSink)

        query.input.receive(input)
        key.input.receive(input)
        value.input.receive(input)

        assertEquals(1, qSink.pending!!.dimensions)
        assertEquals(1, kSink.pending!!.dimensions)
        assertEquals(2, vSink.pending!!.dimensions)
        assertEquals(2.0, qSink.pending!![0][0])
        assertEquals(-1.0, qSink.pending!![1][0])
        assertEquals(3.0, kSink.pending!![0][0])
        assertEquals(4.0, kSink.pending!![1][0])
        assertEquals(5.5, vSink.pending!![0][0])
        assertEquals(0.5, vSink.pending!![0][1])
    }
}
