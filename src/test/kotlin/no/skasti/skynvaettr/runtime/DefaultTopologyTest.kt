package no.skasti.skynvaettr.runtime

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import no.skasti.skynvaettr.environment.InMemoryEnvironment
import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.topology.EntryPoint
import no.skasti.skynvaettr.topology.Node
import no.skasti.skynvaettr.topology.Port
import no.skasti.skynvaettr.topology.Group
import no.skasti.skynvaettr.signals.InMemorySampleStore
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.SampleEntryPoint
import no.skasti.skynvaettr.signals.Signal
import org.junit.jupiter.api.assertDoesNotThrow

class DefaultTopologyTest {
    @Test
    fun `builder flattens group nodes and keeps the group boundary`() {
        val environment = InMemoryEnvironment()
        val entryPoint = RecordingEntryPoint()
        val group = TestGroup("signals", entryPoint)

        val topology = DefaultTopology(environment) {
            add(group)
        }

        assertEquals(listOf(entryPoint), topology.nodes)
        assertEquals(listOf(group), topology.groups)

        environment.append(7)
        topology.update()

        assertEquals(listOf(listOf(7)), entryPoint.received)
    }

    @Test
    fun `builder adds standalone nodes through the same add function`() {
        val environment = InMemoryEnvironment()
        val entryPoint = RecordingEntryPoint()

        val topology = DefaultTopology(environment) {
            add(entryPoint)
        }

        assertEquals(listOf(entryPoint), topology.nodes)
        assertEquals(emptyList(), topology.groups)
    }

    @Test
    fun `ingests a shared sample batch once before dispatching entrypoints`() {
        val environment = InMemoryEnvironment()
        val topologyStore = InMemorySampleStore()
        val first = SampleEntryPoint(topologyStore)
        val second = SampleEntryPoint(topologyStore)
        val topology = DefaultTopology(environment, topologyStore, listOf(first, second))
        val sample = Sample(Signal<Double>("sensor.temperature"), 20.0, Instant.EPOCH)

        environment.append(sample)
        topology.update()

        assertSame(topologyStore, topology.sampleStore)
        assertEquals(listOf(sample), topologyStore.get(Instant.MIN, Instant.MAX))
        assertNotNull(first.latestRepresentation)
        assertNotNull(second.latestRepresentation)
    }

    @Test
    fun `default sample entrypoint uses the topology sample store`() {
        val environment = InMemoryEnvironment()
        val topologyStore = InMemorySampleStore()
        val topology = DefaultTopology(environment, topologyStore)
        val sample = Sample(Signal<Double>("sensor.temperature"), 20.0, Instant.EPOCH)

        environment.append(sample)
        topology.update()

        assertEquals(listOf(sample), topologyStore.get(Instant.MIN, Instant.MAX))
    }

    @Test
    fun `topology retains all nodes and derives entrypoints from them`() {
        val environment = InMemoryEnvironment()
        val entryPoint = RecordingEntryPoint()
        val input = SingleSlotPort<Representation>("input")
        val node = object : Node {
            override val ports: List<Port<*>> = listOf(input)
        }
        entryPoint.output.connectTo(input)
        val nodes = mutableListOf<Node>(entryPoint, node)
        val topology = DefaultTopology(environment, nodes = nodes)
        nodes.clear()

        assertEquals(listOf(entryPoint, node), topology.nodes)
        assertEquals(listOf(entryPoint), topology.entryPoints)

        environment.append(1)
        topology.update()

        assertEquals(listOf(listOf(1)), entryPoint.received)
        assertEquals(1.0, assertNotNull(input.pending)[0][0])
    }

    @Test
    fun `update processes only new values for each typed entrypoint`() {
        val environment = InMemoryEnvironment()
        val entryPoint = RecordingEntryPoint()
        val topology = DefaultTopology(environment, nodes = listOf(entryPoint))

        environment.append(listOf(1, "ignored", 2))

        assertDoesNotThrow { topology.update() }
        assertEquals(listOf(listOf(1, 2)), entryPoint.received)

        environment.append(3)

        assertDoesNotThrow { topology.update() }
        assertEquals(listOf(listOf(1, 2), listOf(3)), entryPoint.received)
    }

    @Test
    fun `multiple entrypoints receive their own typed values`() {
        val environment = InMemoryEnvironment()
        val integers = RecordingEntryPoint()
        val strings = StringEntryPoint()
        val topology = DefaultTopology(environment, nodes = listOf(integers, strings))

        environment.append(listOf(1, "one", 2, "two"))

        assertDoesNotThrow { topology.update() }
        assertEquals(listOf(listOf(1, 2)), integers.received)
        assertEquals(listOf(listOf("one", "two")), strings.received)
    }

    @Test
    fun `entrypoints sharing an input type receive every new batch`() {
        val environment = InMemoryEnvironment()
        val first = RecordingEntryPoint()
        val second = RecordingEntryPoint()
        val strings = StringEntryPoint()
        val topology = DefaultTopology(environment, nodes = listOf(first, strings, second))

        environment.append(listOf(1, "one", 2))
        topology.update()

        assertEquals(listOf(listOf(1, 2)), first.received)
        assertEquals(first.received, second.received)
        assertEquals(listOf(listOf("one")), strings.received)

        topology.update()

        assertEquals(listOf(listOf(1, 2)), first.received)
        assertEquals(first.received, second.received)
        assertEquals(listOf(listOf("one")), strings.received)

        environment.append(3)
        topology.update()

        assertEquals(listOf(listOf(1, 2), listOf(3)), first.received)
        assertEquals(first.received, second.received)
        assertEquals(listOf(listOf("one")), strings.received)
    }

    private class RecordingEntryPoint : EntryPoint<Int> {
        override val inputType = Int::class
        val received = mutableListOf<List<Int>>()
        val output = SingleSlotPort<Representation>("output")
        override val ports: List<Port<*>>
            get() = listOf(output)

        override fun process(items: List<Int>) {
            received += items
            output.emit(Representation.of(Embedding.of(items.first().toDouble())))
        }
    }

    private class StringEntryPoint : EntryPoint<String> {
        override val inputType = String::class
        val received = mutableListOf<List<String>>()
        val output = SingleSlotPort<Representation>("output")
        override val ports: List<Port<*>>
            get() = listOf(output)

        override fun process(items: List<String>) {
            received += items
            output.emit(Representation.of(Embedding.of(items.first().length.toDouble())))
        }
    }

    private class TestGroup(
        override val name: String,
        entryPoint: EntryPoint<*>,
    ) : Group {
        val inputPort = entryPoint.ports.first()
        override val nodes: List<Node> = listOf(entryPoint)
    }
}
