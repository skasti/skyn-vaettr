package no.skasti.skynvaettr.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation

class SingleSlotPortTest {
    @Test
    fun `stores representation and publishes receive event`() {
        val port = SingleSlotPort("input")
        val received = mutableListOf<Port>()
        port.onReceive.subscribe(received::add)
        val representation = representation(1.0)

        port.receive(representation)

        assertEquals(representation, port.pending)
        assertEquals<List<Port>>(listOf(port), received)
    }

    @Test
    fun `rejects a second representation until the first is cleared`() {
        val port = SingleSlotPort("input")
        val first = representation(1.0)
        val second = representation(2.0)

        port.receive(first)

        assertFailsWith<IllegalStateException> {
            port.receive(second)
        }
        assertEquals(first, port.pending)
    }

    @Test
    fun `clearing allows the next representation to be received`() {
        val port = SingleSlotPort("input")
        val received = mutableListOf<Port>()
        port.onReceive.subscribe(received::add)

        port.receive(representation(1.0))
        port.clear()
        port.receive(representation(2.0))

        assertEquals<List<Port>>(listOf(port, port), received)
        assertEquals(representation(2.0), port.pending)
    }

    @Test
    fun `emit delivers to every connected target port`() {
        val output = SingleSlotPort("output")
        val firstInput = SingleSlotPort("first-input")
        val secondInput = SingleSlotPort("second-input")
        val representation = representation(1.0)

        val firstSynapse = output.connectTo(firstInput)
        val secondSynapse = output.connectTo(secondInput)
        output.emit(representation)

        assertEquals(output, firstSynapse.source)
        assertEquals(firstInput, firstSynapse.target)
        assertEquals(output, secondSynapse.source)
        assertEquals(secondInput, secondSynapse.target)
        assertEquals(representation, firstInput.pending)
        assertEquals(representation, secondInput.pending)
    }

    @Test
    fun `unsubscribe stops receive event delivery`() {
        val port = SingleSlotPort("input")
        val received = mutableListOf<Port>()
        val subscription = port.onReceive.subscribe(received::add)

        subscription.unsubscribe()
        port.receive(representation(1.0))

        assertEquals(emptyList(), received)
    }

    private fun representation(value: Double): Representation =
        Representation.of(Embedding.of(value))
}
