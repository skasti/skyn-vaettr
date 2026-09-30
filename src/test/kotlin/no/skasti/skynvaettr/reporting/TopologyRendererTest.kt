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

        assertEquals(3, Regex("n\\d+ \\[label=").findAll(dot).count())
        assertEquals(3, Regex(" -> ").findAll(dot).count())
        assertTrue("n0 -> n1 [xlabel=\"port\"]" in dot)
        assertTrue("n1 -> n0 [xlabel=\"port\"]" in dot)
        assertTrue("n1 -> n1 [xlabel=\"port\"]" in dot)
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
    fun `can omit port labels for compact topology diagrams`() {
        val first = TestNode()
        val second = TestNode()
        first.port.connectTo(second.port)

        val dot = TopologyRenderer(showPortLabels = false).toDot(topology(first, second))

        assertTrue("n0 -> n1;" in dot)
        assertTrue("xlabel=\"port\"" !in dot)
    }

    @Test
    fun `renders a PR 17 shaped grouped topology with port labels`() {
        val topology = selfSupervisedTrainingTopology()
        val dot = TopologyRenderer().toDot(topology)
        val directory = Path.of("build", "test-artifacts", "TopologyRendererTest", "self-supervised-training")

        assertEquals(9, topology.nodes.size)
        assertEquals(listOf("Sensory Processing", "Prediction", "Learning"), topology.groups.keys.toList())
        assertTrue("label=\"InspectableSampleEntryPoint\"" in dot)
        assertTrue("label=\"SelfSupervisedSignalTrainer\"" in dot)
        assertTrue("xlabel=\"q-forward-pass\"" in dot)
        assertTrue("xlabel=\"q-parameter-snapshot\"" in dot)
        assertTrue("xlabel=\"q-snapshot-request\"" in dot)
        assertTrue("xlabel=\"q-update\"" in dot)
        assertTrue("n2 -> n8 [xlabel=\"q-forward-pass\"]" in dot)
        assertTrue("n2 -> n8 [xlabel=\"q-parameter-snapshot\"]" in dot)
        assertTrue("n8 -> n2 [xlabel=\"q-snapshot-request\"]" in dot)
        assertTrue("n8 -> n2 [xlabel=\"q-update\"]" in dot)

        Files.createDirectories(directory)
        Files.writeString(directory.resolve("topology.dot"), dot)
        val executable = System.getenv("GRAPHVIZ_DOT") ?: "dot"
        assumeTrue(graphvizAvailable(executable), "Graphviz is unavailable; topology.dot was still written")
        TopologyRenderer().render(topology, directory)
        assertTrue(Files.isRegularFile(directory.resolve("topology.png")))
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
        override val groups = groups.associateBy { it.name }
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

    private fun selfSupervisedTrainingTopology(): Topology {
        fun node(name: String, vararg ports: String) = DiagramNode(name, ports.toList())

        val entry = node("InspectableSampleEntryPoint", "samples", "sample-position-metadata", "new-samples")
        val stream = node(
            "OnlineSignalPredictionStream",
            "new-samples", "metadata", "representation", "observations", "frame", "prefix",
        )
        val query = node(
            "Query", "q-input", "q", "q-forward-pass", "q-parameter-snapshot-request",
            "q-parameter-update", "q-parameter-snapshot",
        )
        val key = node(
            "Key", "k-input", "k", "k-forward-pass", "k-parameter-snapshot-request",
            "k-parameter-update", "k-parameter-snapshot",
        )
        val value = node(
            "Value", "v-input", "v", "v-forward-pass", "v-parameter-snapshot-request",
            "v-parameter-update", "v-parameter-snapshot",
        )
        val attention = node("Attention", "q", "k", "v", "attention", "weights", "attention-forward-pass")
        val decoderProjection = node(
            "DecoderProjection", "decoder-input", "decoder", "decoder-forward-pass",
            "decoder-parameter-snapshot-request", "decoder-parameter-update", "decoder-parameter-snapshot",
        )
        val decoder = node("SignalPredictionDecoder", "frame", "attention-pass", "decoder-pass", "predictions")
        val trainer = node(
            "SelfSupervisedSignalTrainer", "q-pass", "k-pass", "v-pass", "decoded", "observations",
            "q-snapshot-request", "k-snapshot-request", "v-snapshot-request", "decoder-snapshot-request",
            "q-snapshot", "k-snapshot", "v-snapshot", "decoder-snapshot",
            "q-update", "k-update", "v-update", "decoder-update",
        )

        val sensory = Group.build("Sensory Processing") {
            add(entry); add(stream)
            entry.diagramPort("new-samples").connectTo(stream.diagramPort("new-samples"))
            entry.diagramPort("sample-position-metadata").connectTo(stream.diagramPort("metadata"))
            entry.diagramPort("samples").connectTo(stream.diagramPort("representation"))
            expose("prefix", stream.diagramPort("prefix"))
            expose("frame", stream.diagramPort("frame"))
            expose("observations", stream.diagramPort("observations"))
        }
        val prediction = Group.build("Prediction") {
            add(query); add(key); add(value); add(attention); add(decoderProjection); add(decoder)
            query.diagramPort("q").connectTo(attention.diagramPort("q"))
            key.diagramPort("k").connectTo(attention.diagramPort("k"))
            value.diagramPort("v").connectTo(attention.diagramPort("v"))
            attention.diagramPort("attention").connectTo(decoderProjection.diagramPort("decoder-input"))
            attention.diagramPort("attention-forward-pass").connectTo(decoder.diagramPort("attention-pass"))
            decoderProjection.diagramPort("decoder-forward-pass").connectTo(decoder.diagramPort("decoder-pass"))
            expose("query-input", query.diagramPort("q-input"))
            expose("key-input", key.diagramPort("k-input"))
            expose("value-input", value.diagramPort("v-input"))
            expose("frame", decoder.diagramPort("frame"))
            expose("predictions", decoder.diagramPort("predictions"))
            expose("query-forward", query.diagramPort("q-forward-pass"))
            expose("key-forward", key.diagramPort("k-forward-pass"))
            expose("value-forward", value.diagramPort("v-forward-pass"))
            expose("query-snapshot-request", query.diagramPort("q-parameter-snapshot-request"))
            expose("key-snapshot-request", key.diagramPort("k-parameter-snapshot-request"))
            expose("value-snapshot-request", value.diagramPort("v-parameter-snapshot-request"))
            expose("decoder-snapshot-request", decoderProjection.diagramPort("decoder-parameter-snapshot-request"))
            expose("query-snapshot", query.diagramPort("q-parameter-snapshot"))
            expose("key-snapshot", key.diagramPort("k-parameter-snapshot"))
            expose("value-snapshot", value.diagramPort("v-parameter-snapshot"))
            expose("decoder-snapshot", decoderProjection.diagramPort("decoder-parameter-snapshot"))
            expose("query-update", query.diagramPort("q-parameter-update"))
            expose("key-update", key.diagramPort("k-parameter-update"))
            expose("value-update", value.diagramPort("v-parameter-update"))
            expose("decoder-update", decoderProjection.diagramPort("decoder-parameter-update"))
        }
        val learning = Group.build("Learning") {
            add(trainer)
            listOf("observations", "predictions", "query-forward", "key-forward", "value-forward",
                "query-snapshot-request", "key-snapshot-request", "value-snapshot-request", "decoder-snapshot-request",
                "query-snapshot", "key-snapshot", "value-snapshot", "decoder-snapshot",
                "query-update", "key-update", "value-update", "decoder-update").forEach { name ->
                expose(name, trainer.diagramPort(name.toPortName()))
            }
        }

        sensory.port<Any>("prefix").connectTo(
            prediction.port("query-input"), prediction.port("key-input"), prediction.port("value-input"),
        )
        sensory.port<Any>("frame").connectTo(prediction.port("frame"))
        sensory.port<Any>("observations").connectTo(learning.port("observations"))
        prediction.port<Any>("predictions").connectTo(learning.port("predictions"))
        prediction.port<Any>("query-forward").connectTo(learning.port("query-forward"))
        prediction.port<Any>("key-forward").connectTo(learning.port("key-forward"))
        prediction.port<Any>("value-forward").connectTo(learning.port("value-forward"))
        listOf("query", "key", "value", "decoder").forEach { role ->
            learning.port<Any>("$role-snapshot-request")
                .connectTo(prediction.port("$role-snapshot-request"))
            prediction.port<Any>("$role-snapshot").connectTo(learning.port("$role-snapshot"))
            learning.port<Any>("$role-update").connectTo(prediction.port("$role-update"))
        }
        return object : Topology {
            override val nodes = listOf(entry, stream, query, key, value, attention, decoderProjection, decoder, trainer)
            override val groups = linkedMapOf(
                sensory.name to sensory,
                prediction.name to prediction,
                learning.name to learning,
            )
            override fun update() = Unit
        }
    }

    private fun String.toPortName(): String = when (this) {
        "query-forward" -> "q-pass"
        "key-forward" -> "k-pass"
        "value-forward" -> "v-pass"
        "query-snapshot-request" -> "q-snapshot-request"
        "key-snapshot-request" -> "k-snapshot-request"
        "value-snapshot-request" -> "v-snapshot-request"
        "decoder-snapshot-request" -> "decoder-snapshot-request"
        "query-snapshot" -> "q-snapshot"
        "key-snapshot" -> "k-snapshot"
        "value-snapshot" -> "v-snapshot"
        "query-update" -> "q-update"
        "key-update" -> "k-update"
        "value-update" -> "v-update"
        "decoder-update" -> "decoder-update"
        "observations" -> "observations"
        "predictions" -> "decoded"
        else -> error("No trainer port for group port '$this'")
    }

    private class DiagramNode(
        override val name: String,
        portNames: List<String>,
    ) : Node {
        private val namedPorts = portNames.associateWith { SingleSlotPort<Any>(it) }
        override val ports = namedPorts.values.toList()
        fun diagramPort(name: String): SingleSlotPort<Any> = checkNotNull(namedPorts[name]) {
            "Unknown diagram port '$name' on $this"
        }
    }
}
