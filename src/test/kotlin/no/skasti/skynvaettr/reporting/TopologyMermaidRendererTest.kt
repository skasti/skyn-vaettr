package no.skasti.skynvaettr.reporting

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import no.skasti.skynvaettr.runtime.SingleSlotPort
import no.skasti.skynvaettr.topology.Group
import no.skasti.skynvaettr.topology.Node
import no.skasti.skynvaettr.topology.Port
import no.skasti.skynvaettr.topology.Topology

class TopologyMermaidRendererTest {
    @Test
    fun `renders external collectors alongside internal connections without changing delivery`() {
        val source = DiagramNode("Source")
        val target = DiagramNode("Target")
        val collector = SingleSlotPort<Int>("Exported samples")
        source.output.connectTo(target.output, collector)
        val topology = topology(source, target)
        val renderer = TopologyMermaidRenderer(topology)

        val diagram = renderer.toMermaid()

        assertTrue("n0 -->|\"output\"| n1" in diagram)
        assertTrue("externalPort0@{ shape: das, label: \"Exported samples\" }" in diagram)
        assertTrue("n0 -->|\"output\"| externalPort0" in diagram)
        assertTrue("classDef externalPort fill:#D5F5F0,stroke:#45A99B" in diagram)
        assertTrue("class externalPort0 externalPort" in diagram)
        assertTrue("class n0,n1 topologyNode" in diagram)
        assertEquals(diagram, renderer.toMermaid())
        assertEquals(2, source.output.synapses.size)
        source.output.emit(42)
        assertEquals(42, target.output.pending)
        assertEquals(42, collector.pending)
    }

    @Test
    fun `captures topology on first export`() {
        val source = DiagramNode("Source")
        val topology = topology(source)
        val renderer = TopologyMermaidRenderer(topology)
        source.output.connectTo(SingleSlotPort<Int>("Early collector"))

        val snapshot = renderer.toMermaid()
        source.output.connectTo(SingleSlotPort<Int>("Late collector"))

        assertTrue("Early collector" in snapshot)
        assertEquals(snapshot, renderer.toMermaid())
        assertTrue("Late collector" in TopologyMermaidRenderer(topology).toMermaid())
    }

    @Test
    fun `shares an endpoint by port identity and keeps equally named endpoints distinct`() {
        val first = DiagramNode("First")
        val second = DiagramNode("Second")
        val shared = SingleSlotPort<Int>("Collector")
        val distinct = SingleSlotPort<Int>("Collector")
        first.output.connectTo(shared, distinct, shared)
        second.output.connectTo(shared)

        val diagram = TopologyMermaidRenderer(topology(first, second)).toMermaid()

        assertEquals(2, Regex("shape: das").findAll(diagram).count())
        assertTrue("n0 -->|\"output\"| externalPort0" in diagram)
        assertTrue("n0 -->|\"output\"| externalPort1" in diagram)
        assertTrue("n1 -->|\"output\"| externalPort0" in diagram)
        assertEquals(3, Regex(" -->").findAll(diagram).count())
    }

    @Test
    fun `escapes collector labels and places exports outside topology groups`() {
        val source = DiagramNode("Source")
        val collector = SingleSlotPort<Int>("Report \"A&B\" | <values>\r\nnext")
        // The endpoint has an owner, but that owner is outside the rendered topology.
        val outside = DiagramNode("Outside", collector)
        val downstream = SingleSlotPort<Int>("Beyond the export")
        collector.connectTo(downstream)
        source.output.connectTo(outside.output)
        val group = Group.build("Processing") { add(source) }
        val topology = object : Topology {
            override val nodes = listOf(source)
            override val groups = mapOf(group.name to group)
            override fun update() = Unit
        }

        val diagram = TopologyMermaidRenderer(topology).toMermaid()

        assertTrue("  end\n  externalPort0@{" in diagram)
        assertTrue("label: \"Report #quot;A#38;B#quot; #124; #60;values#62;<br/>next\"" in diagram)
        assertFalse("Outside" in diagram)
        assertFalse("Beyond the export" in diagram)
    }

    @Test
    fun `internal-only and empty topologies do not gain export styling`() {
        val first = DiagramNode("First")
        val second = DiagramNode("Second")
        first.output.connectTo(second.output)

        for (topology in listOf(topology(first, second), topology())) {
            val diagram = TopologyMermaidRenderer(topology).toMermaid()
            assertTrue(diagram.startsWith("flowchart LR\n"))
            assertFalse("externalPort" in diagram)
            assertFalse("shape: das" in diagram)
        }
    }

    @Test
    fun `still rejects duplicate topology nodes and shared ownership of internal ports`() {
        val first = DiagramNode("First")
        val second = DiagramNode("Second", first.output)

        assertFailsWith<IllegalArgumentException> {
            TopologyMermaidRenderer(topology(first, first)).toMermaid()
        }
        assertFailsWith<IllegalArgumentException> {
            TopologyMermaidRenderer(topology(first, second)).toMermaid()
        }
    }

    private class DiagramNode(
        override val name: String,
        val output: SingleSlotPort<Int> = SingleSlotPort("output"),
    ) : Node {
        override val ports: List<Port<*>> = listOf(output)
    }

    private fun topology(vararg nodes: Node): Topology = object : Topology {
        override val nodes = nodes.toList()
        override fun update() = Unit
    }
}
