package no.skasti.skynvaettr.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.topology.Node
import no.skasti.skynvaettr.topology.Port

class SingleSlotReceiverTest {
    @Test
    fun `stores representation and notifies owner`() {
        val node = RecordingNode()
        val input = node.input
        val representation = representation(1.0)

        input.receive(representation)

        assertEquals(representation, input.pending)
        assertEquals(listOf<Port<*>>(input), node.received)
    }

    @Test
    fun `rejects a second representation until the first is cleared`() {
        val node = RecordingNode()
        val input = node.input
        val first = representation(1.0)
        val second = representation(2.0)

        input.receive(first)

        assertFailsWith<IllegalStateException> {
            input.receive(second)
        }
        assertEquals(first, input.pending)
    }

    @Test
    fun `clearing allows the next representation to be received`() {
        val node = RecordingNode()
        val input = node.input
        val first = representation(1.0)
        val second = representation(2.0)

        input.receive(first)
        input.clear()
        input.receive(second)

        assertEquals(listOf<Port<*>>(input, input), node.received)
        assertEquals(second, input.pending)
    }

    @Test
    fun `retains representation when owner notification fails`() {
        val node = FailingNode()
        val input = node.input
        val representation = representation(1.0)

        assertFailsWith<IllegalStateException> {
            input.receive(representation)
        }

        assertEquals(representation, input.pending)
    }

    private fun representation(value: Double): Representation =
        Representation.of(Embedding.of(value))

    private class RecordingNode : Node {
        val received = mutableListOf<Port<*>>()
        val input = SingleSlotPort<Representation>("input")
        override val ports: List<Port<*>>
            get() = listOf(input)

        init {
            input.onReceive.subscribe { port -> received += port }
        }
    }

    private class FailingNode : Node {
        val input = SingleSlotPort<Representation>("input")
        override val ports: List<Port<*>>
            get() = listOf(input)

        init {
            input.onReceive.subscribe { error("processing failed") }
        }
    }
}
