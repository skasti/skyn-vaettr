package no.skasti.skynvaettr.reporting

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import no.skasti.skynvaettr.runtime.SingleSlotPort
import no.skasti.skynvaettr.topology.Node
import no.skasti.skynvaettr.topology.Topology
import no.skasti.skynvaettr.topology.Group
import org.junit.jupiter.api.Assumptions.assumeTrue

class TopologyRendererTest {
    @Test
    fun `draws distinct nodes and directed connections through their ports`() {
        val first = TestNode()
        val second = TestNode()
        val isolated = TestNode()
        first.port.connectTo(second.port)
        first.port.connectTo(second.port)
        second.port.connectTo(first.port)
        second.port.connectTo(second.port)

        val dot = TopologyRenderer().toDot(topology(first, second, isolated))

        assertEquals(3, Regex("label=").findAll(dot).count())
        assertEquals(3, Regex(" -> ").findAll(dot).count())
        assertTrue("n0 -> n1;" in dot)
        assertTrue("n1 -> n0;" in dot)
        assertTrue("n1 -> n1;" in dot)
        assertTrue("n2 [label=\"TestNode\"];" in dot)
    }

    @Test
    fun `uses a node name instead of its class name`() {
        val source = NamedNode("Ingress")
        val target = NamedNode("Attention")
        source.port.connectTo(target.port)

        val dot = TopologyRenderer().toDot(topology(source, target))

        assertTrue("n0 [label=\"Ingress\"];" in dot)
        assertTrue("n1 [label=\"Attention\"];" in dot)
        assertTrue("NamedNode" !in dot)
    }

    @Test
    fun `rejects a connection to a node omitted from the topology`() {
        val source = TestNode()
        source.port.connectTo(TestNode().port)

        assertFailsWith<IllegalArgumentException> {
            TopologyRenderer().toDot(topology(source))
        }
    }

    @Test
    fun `renders topology groups as graphviz clusters`() {
        val first = TestNode()
        val second = TestNode()
        first.port.connectTo(second.port)
        val group = TestGroup("Memory system", listOf(first, second))

        val dot = TopologyRenderer().toDot(topology(listOf(first, second), listOf(group)))

        assertTrue("subgraph cluster_0" in dot)
        assertTrue("label=\"Memory system\"" in dot)
        assertTrue("n0 [label=\"TestNode\"]" in dot)
        assertTrue("n1 [label=\"TestNode\"]" in dot)
    }

    @Test
    fun `applies custom graph styling`() {
        val first = TestNode()
        val second = TestNode()
        first.port.connectTo(second.port)
        val style = TopologyGraphStyle(
            graphAttributes = mapOf("rankdir" to "TB"),
            nodeAttributes = mapOf("shape" to "ellipse", "style" to "filled"),
            edgeAttributes = mapOf("color" to "red", "penwidth" to "2"),
            groupAttributes = mapOf("style" to "dashed", "color" to "blue"),
        )

        val dot = TopologyRenderer(style = style).toDot(topology(first, second))

        assertTrue("graph [rankdir=TB];" in dot)
        assertTrue("node [shape=ellipse, style=filled];" in dot)
        assertTrue("edge [color=red, penwidth=2];" in dot)
    }

    @Test
    fun `writes grouped topology visualization to build artifacts`() {
        val first = TestNode()
        val second = TestNode()
        val isolated = TestNode()
        first.port.connectTo(second.port)
        second.port.connectTo(isolated.port)
        val topology = topology(
            listOf(first, second, isolated),
            listOf(TestGroup("Memory system", listOf(first, second))),
        )
        val renderer = TopologyRenderer()
        val directory = Path.of("build", "test-artifacts", "TopologyRendererTest", "grouped-topology")
        Files.createDirectories(directory)
        Files.writeString(directory.resolve("topology.dot"), renderer.toDot(topology))

        val executable = System.getenv("GRAPHVIZ_DOT") ?: "dot"
        assumeTrue(graphvizAvailable(executable), "Graphviz is unavailable; topology.dot was still written")

        renderer.render(topology, directory)
        assertTrue(Files.isRegularFile(directory.resolve("topology.png")))
    }

    private fun graphvizAvailable(executable: String): Boolean = try {
        val process = ProcessBuilder(executable, "-V")
            .redirectErrorStream(true)
            .start()
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            false
        } else {
            process.exitValue() == 0
        }
    } catch (_: Exception) {
        false
    }

    private fun topology(vararg nodes: Node): Topology = object : Topology {
        override val nodes = nodes.toList()
        override fun update() = Unit
    }

    private fun topology(nodes: List<Node>, groups: List<Group>): Topology = object : Topology {
        override val nodes = nodes
        override val groups = groups
        override fun update() = Unit
    }

    private class TestNode : Node {
        override val name = "TestNode"
        val port = SingleSlotPort<Int>("port")
        override val ports = listOf(port)
    }

    private class NamedNode(override val name: String) : Node {
        val port = SingleSlotPort<Int>("port")
        override val ports = listOf(port)
    }

    private data class TestGroup(
        override val name: String,
        override val nodes: List<Node>,
    ) : Group
}
