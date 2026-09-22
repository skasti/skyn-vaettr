package no.skasti.skynvaettr.runtime

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import no.skasti.skynvaettr.environment.InMemoryEnvironment
import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.topology.Node
import no.skasti.skynvaettr.topology.Port
import no.skasti.skynvaettr.topology.Group
import no.skasti.skynvaettr.topology.debugging.RecordingEntryPoint
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.SampleEntryPoint
import no.skasti.skynvaettr.signals.InMemorySampleStore
import no.skasti.skynvaettr.signals.Signal
import org.junit.jupiter.api.assertDoesNotThrow

class DefaultTopologyTest {
    @Test
    fun `builder flattens group nodes and keeps the group boundary`() {
        val environment = InMemoryEnvironment()
        val entryPoint = RecordingEntryPoint("RecordingEntryPoint", Int::class)
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
        val entryPoint = RecordingEntryPoint("RecordingEntryPoint", Int::class)

        val topology = DefaultTopology(environment) {
            add(entryPoint)
        }

        assertEquals(listOf(entryPoint), topology.nodes)
        assertEquals(emptyList(), topology.groups)
    }

    @Test
    fun `ingests a shared sample batch once before dispatching entrypoints`() {
        val environment = InMemoryEnvironment()
        val first = SampleEntryPoint(environment)
        val second = SampleEntryPoint(environment)
        val topology = DefaultTopology(environment, nodes = listOf(first, second))
        val sample = Sample(Signal<Double>("sensor.temperature"), 20.0, Instant.EPOCH)

        environment.append(sample)
        topology.update()

        assertSame(environment, topology.sampleStore)
        assertEquals(listOf(sample), environment.get(Instant.MIN, Instant.MAX))
        assertNotNull(first.latestRepresentation)
        assertNotNull(second.latestRepresentation)
    }

    @Test
    fun `default sample entrypoint uses the topology sample store`() {
        val environment = InMemoryEnvironment()
        val topology = DefaultTopology(environment)
        val sample = Sample(Signal<Double>("sensor.temperature"), 20.0, Instant.EPOCH)

        environment.append(sample)
        topology.update()

        assertEquals(listOf(sample), environment.get(Instant.MIN, Instant.MAX))
    }

    @Test
    fun `rejects sample entrypoints that use a different store`() {
        val environment = InMemoryEnvironment()
        val otherStore = InMemorySampleStore()

        assertFailsWith<IllegalArgumentException> {
            DefaultTopology(
                environment,
                nodes = listOf(SampleEntryPoint(otherStore)),
            )
        }
    }

    @Test
    fun `rejects entrypoints with any as input type`() {
        val environment = InMemoryEnvironment()
        val anyEntryPoint = RecordingEntryPoint("AnyEntryPoint", Any::class)

        assertFailsWith<IllegalArgumentException> {
            DefaultTopology(environment, nodes = listOf(anyEntryPoint))
        }
    }

    @Test
    fun `delivers values to overlapping inherited entrypoint types`() {
        val environment = InMemoryEnvironment()
        val base = RecordingEntryPoint("BaseEntryPoint", BaseValue::class)
        val derived = RecordingEntryPoint("DerivedEntryPoint", DerivedValue::class)
        val topology = DefaultTopology(environment, nodes = listOf(base, derived))
        val value = DerivedValue()

        environment.append(value)
        topology.update()

        assertSame(value, base.received.single().single())
        assertSame(value, derived.received.single().single())
    }

    @Test
    fun `delivers values to overlapping unrelated interface entrypoint types`() {
        val environment = InMemoryEnvironment()
        val left = RecordingEntryPoint("LeftEntryPoint", LeftMarker::class)
        val right = RecordingEntryPoint("RightEntryPoint", RightMarker::class)
        val topology = DefaultTopology(
            environment,
            nodes = listOf(left, right),
        )
        val value = BothValue()

        environment.append(value)
        topology.update()

        assertSame(value, left.received.single().single())
        assertSame(value, right.received.single().single())
    }

    @Test
    fun `topology retains all nodes and derives entrypoints from them`() {
        val environment = InMemoryEnvironment()
        val entryPoint = RecordingEntryPoint(
            "RecordingEntryPoint",
            Int::class,
            ports = listOf(SingleSlotPort<Representation>("output")),
            onProcess = { items ->
                port<Representation>("output")
                    .emit(Representation.of(Embedding.of(items.first().toDouble())))
            },
        )
        val input = SingleSlotPort<Representation>("input")
        val node = object : Node {
            override val name = "TestNode"
            override val ports: List<Port<*>> = listOf(input)
        }
        input.receiveFrom(entryPoint["output"])
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
        val entryPoint = RecordingEntryPoint("RecordingEntryPoint", Int::class)
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
        val integers = RecordingEntryPoint("IntegerEntryPoint", Int::class)
        val strings = RecordingEntryPoint("StringEntryPoint", String::class)
        val topology = DefaultTopology(environment, nodes = listOf(integers, strings))

        environment.append(listOf(1, "one", 2, "two"))

        assertDoesNotThrow { topology.update() }
        assertEquals(listOf(listOf(1, 2)), integers.received)
        assertEquals(listOf(listOf("one", "two")), strings.received)
    }

    @Test
    fun `entrypoints sharing an input type receive every new batch`() {
        val environment = InMemoryEnvironment()
        val first = RecordingEntryPoint("FirstIntegerEntryPoint", Int::class)
        val second = RecordingEntryPoint("SecondIntegerEntryPoint", Int::class)
        val strings = RecordingEntryPoint("StringEntryPoint", String::class)
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

    private open class BaseValue

    private class DerivedValue : BaseValue()

    private interface LeftMarker

    private interface RightMarker

    private class BothValue : LeftMarker, RightMarker

    private class TestGroup(
        override val name: String,
        node: Node,
    ) : Group {
        override val nodes: List<Node> = listOf(node)
    }
}
