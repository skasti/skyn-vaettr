package no.skasti.skynvaettr.attention

import no.skasti.skynvaettr.runtime.SingleSlotPort
import no.skasti.skynvaettr.runtime.DefaultTopology
import no.skasti.skynvaettr.signals.SampleEntryPoint

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import no.skasti.skynvaettr.environment.InMemoryEnvironment
import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.InMemorySampleStore
import no.skasti.skynvaettr.signals.Signal
import no.skasti.skynvaettr.topology.Node
import java.time.Instant

class AttentionNodeTest {
    @Test
    fun `waits for all inputs in any order and consumes a fresh set each round`() {
        val orders = listOf(listOf(0, 1, 2), listOf(0, 2, 1), listOf(1, 0, 2),
            listOf(1, 2, 0), listOf(2, 0, 1), listOf(2, 1, 0))
        orders.forEach { order ->
            val calls = mutableListOf<List<Representation>>()
            val result = representation(42.0)
            val node = AttentionNode(object : Attention {
                override fun apply(queries: Representation, keys: Representation, values: Representation): AttentionResult {
                    calls += listOf(queries, keys, values)
                    return AttentionResult(result, listOf(doubleArrayOf(1.0)))
                }
            })
            val sink = SingleSlotPort<Representation>("sink")
            node.attention.connectTo(sink)
            val inputs = listOf(node.q, node.k, node.v)
            repeat(2) { round ->
                val values = listOf(representation(round + 1.0), representation(2.0), representation(3.0))
                order.take(2).forEach { inputs[it].receive(values[it]) }
                assertEquals(round, calls.size)
                assertNull(sink.pending)
                inputs[order.last()].receive(values[order.last()])
                assertEquals(round + 1, calls.size)
                values.forEachIndexed { index, value -> assertSame(value, calls.last()[index]) }
                assertSame(result, sink.pending)
                inputs.forEach { assertNull(it.pending) }
                sink.clear()
            }
        }
    }

    @Test
    fun `failed computation retains all inputs and emits nothing`() {
        val node = AttentionNode(ScaledDotProductAttention())
        val sink = SingleSlotPort<Representation>("sink")
        node.attention.connectTo(sink)
        val query = representation(1.0)
        val key = Representation.of(Embedding.of(1.0, 2.0))
        val value = representation(3.0)
        node.q.receive(query)
        node.k.receive(key)
        assertFailsWith<IllegalArgumentException> { node.v.receive(value) }
        assertSame(query, node.q.pending)
        assertSame(key, node.k.pending)
        assertSame(value, node.v.pending)
        assertNull(sink.pending)
    }

    @Test
    fun `sample entrypoint can drive self attention through synapses`() {
        val environment = InMemoryEnvironment()
        val sampleStore = InMemorySampleStore()
        val entryPoint = SampleEntryPoint(sampleStore)
        val query = QueryNode()
        val key = KeyNode()
        val value = ValueNode()
        val node = AttentionNode(ScaledDotProductAttention())
        entryPoint.output.connectTo(query.input)
        entryPoint.output.connectTo(key.input)
        entryPoint.output.connectTo(value.input)
        query.output.connectTo(node.q)
        key.output.connectTo(node.k)
        value.output.connectTo(node.v)
        val sink = SingleSlotPort<Representation>("sink")
        node.attention.connectTo(sink)
        val sinkNode = object : Node {
            override val ports = listOf(sink)
        }
        val topology = DefaultTopology(
            environment,
            sampleStore,
            listOf(entryPoint, query, key, value, node, sinkNode),
        )
        environment.append(Sample(Signal<Double>("temperature"), 20.0, Instant.EPOCH))

        topology.update()

        assertEquals(entryPoint.latestRepresentation?.toList(), sink.pending?.toList())
    }

    private fun representation(value: Double) = Representation.of(Embedding.of(value))
}
