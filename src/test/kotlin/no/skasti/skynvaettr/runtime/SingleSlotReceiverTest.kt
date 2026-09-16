package no.skasti.skynvaettr.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation

class SingleSlotReceiverTest {
    @Test
    fun `stores representation and notifies owner`() {
        val node = RecordingNode()
        val receiver = SingleSlotReceiver(node, "input")
        val representation = representation(1.0)

        receiver.receive(representation)

        assertEquals(representation, receiver.pending)
        assertEquals(listOf<RepresentationReceiver>(receiver), node.received)
    }

    @Test
    fun `rejects a second representation until the first is cleared`() {
        val receiver = SingleSlotReceiver(RecordingNode(), "input")
        val first = representation(1.0)
        val second = representation(2.0)

        receiver.receive(first)

        assertFailsWith<IllegalStateException> {
            receiver.receive(second)
        }
        assertEquals(first, receiver.pending)
    }

    @Test
    fun `clearing allows the next representation to be received`() {
        val node = RecordingNode()
        val receiver = SingleSlotReceiver(node, "input")
        val first = representation(1.0)
        val second = representation(2.0)

        receiver.receive(first)
        receiver.clear()
        receiver.receive(second)

        assertEquals(listOf<RepresentationReceiver>(receiver, receiver), node.received)
        assertEquals(second, receiver.pending)
    }

    @Test
    fun `retains representation when owner notification fails`() {
        val receiver = SingleSlotReceiver(FailingNode(), "input")
        val representation = representation(1.0)

        assertFailsWith<IllegalStateException> {
            receiver.receive(representation)
        }

        assertEquals(representation, receiver.pending)
    }

    private fun representation(value: Double): Representation =
        Representation.of(Embedding.of(value))

    private class RecordingNode : Node {
        val received = mutableListOf<RepresentationReceiver>()

        override fun onInputReceived(receiver: RepresentationReceiver) {
            received += receiver
        }
    }

    private class FailingNode : Node {
        override fun onInputReceived(receiver: RepresentationReceiver) {
            error("processing failed")
        }
    }
}
