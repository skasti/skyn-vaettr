package no.skasti.skynvaettr.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.topology.Port

class SingleSlotPortTest {
    @Test
    fun `stores representation and publishes receive event`() {
        val port = SingleSlotPort<Representation>("input")
        val received = mutableListOf<Port<*>>()
        port.onReceive.subscribe(received::add)
        val representation = representation(1.0)

        port.receive(representation)

        assertEquals(representation, port.pending)
        assertEquals<List<Port<*>>>(listOf(port), received)
    }

    @Test
    fun `rejects a second representation until the first is cleared`() {
        val port = SingleSlotPort<Representation>("input")
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
        val port = SingleSlotPort<Representation>("input")
        val received = mutableListOf<Port<*>>()
        port.onReceive += received::add

        val first = representation(1.0)
        port.receive(first)
        port.clear()
        val second = representation(2.0)
        port.receive(second)

        assertEquals(listOf<Port<*>>(port, port), received)
        assertEquals(second, port.pending)
    }

    @Test
    fun `emit delivers to every connected target port`() {
        val output = SingleSlotPort<Representation>("output")
        val firstInput = SingleSlotPort<Representation>("first-input")
        val secondInput = SingleSlotPort<Representation>("second-input")
        val representation = representation(1.0)

        output.connectTo(firstInput)
        output.connectTo(secondInput)
        output.emit(representation)

        assertEquals(listOf(firstInput, secondInput), output.synapses.map { it.target })
        assertEquals(representation, firstInput.pending)
        assertEquals(representation, secondInput.pending)
    }

    @Test
    fun `connectTo can declare several targets`() {
        val output = SingleSlotPort<Representation>("output")
        val firstInput = SingleSlotPort<Representation>("first-input")
        val secondInput = SingleSlotPort<Representation>("second-input")

        output.connectTo(firstInput, secondInput)

        assertEquals(listOf(firstInput, secondInput), output.synapses.map { it.target })
    }

    @Test
    fun `connectTo rejects an empty target list`() {
        val output = SingleSlotPort<Representation>("output")

        assertFailsWith<IllegalArgumentException> {
            output.connectTo()
        }
    }

    @Test
    fun `unsubscribe stops receive event delivery`() {
        val port = SingleSlotPort<Representation>("input")
        val received = mutableListOf<Port<*>>()
        val subscription = port.onReceive.subscribe(received::add)

        subscription.unsubscribe()
        port.receive(representation(1.0))

        assertEquals(emptyList(), received)
    }

    private fun representation(value: Double): Representation =
        Representation.of(Embedding.of(value))
}
