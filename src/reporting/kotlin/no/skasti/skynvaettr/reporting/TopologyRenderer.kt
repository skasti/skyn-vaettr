package no.skasti.skynvaettr.reporting

import java.nio.file.Files
import java.nio.file.Path
import java.util.IdentityHashMap
import java.util.concurrent.TimeUnit
import no.skasti.skynvaettr.topology.Node
import no.skasti.skynvaettr.topology.Port
import no.skasti.skynvaettr.topology.Topology

/** Renders nodes and directed connections using Graphviz's dot executable. */
class TopologyRenderer(
    private val executable: String = System.getenv("GRAPHVIZ_DOT") ?: "dot",
    private val style: TopologyGraphStyle = TopologyGraphStyle(),
) {
    fun toDot(topology: Topology): String {
        val nodes = topology.nodes.toList()
        val ids = IdentityHashMap<Node, Int>()
        val owners = IdentityHashMap<Port<*>, Int>()
        nodes.forEachIndexed { index, node ->
            require(ids.put(node, index) == null) { "Topology contains the same node more than once" }
            node.ports.forEach { port ->
                val previous = owners.put(port, index)
                require(previous == null || previous == index) { "Port '${port.name}' belongs to multiple nodes" }
            }
        }
        val groupedNodes = IdentityHashMap<Node, String>()
        topology.groups.forEach { group ->
            require(group.name.isNotBlank()) { "Topology group name must not be blank" }
            group.nodes.forEach { node ->
                require(ids.containsKey(node)) {
                    "Topology group '${group.name}' contains a node outside the topology"
                }
                val previous = groupedNodes.put(node, group.name)
                require(previous == null) {
                    "Node belongs to both topology groups '$previous' and '${group.name}'"
                }
            }
        }
        val edges = linkedSetOf<Pair<Int, Int>>()
        nodes.forEach { node ->
            node.ports.forEach { port ->
                port.synapses.forEach { synapse ->
                    require(synapse.source === port) { "Outgoing synapse has a different source port" }
                    val target = requireNotNull(owners[synapse.target]) {
                        "Target port '${synapse.target.name}' is not owned by a node in this topology"
                    }
                    edges += owners.getValue(port) to target
                }
            }
        }
        return buildString {
            appendLine("digraph Topology {")
            appendAttributeBlock("  graph", style.graphAttributes)
            appendAttributeBlock("  node", style.nodeAttributes)
            appendAttributeBlock("  edge", style.edgeAttributes)
            fun label(node: Node): String = node.name
                .replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n")

            topology.groups.forEachIndexed { groupIndex, group ->
                appendLine("  subgraph cluster_$groupIndex {")
                appendLine("    label=\"${escape(group.name)}\";")
                appendAttributeAssignments("    ", style.groupAttributes)
                group.nodes.forEach { node ->
                    appendLine("    n${ids.getValue(node)} [label=\"${label(node)}\"];")
                }
                appendLine("  }")
            }
            nodes.filter { !groupedNodes.containsKey(it) }.forEach { node ->
                val index = ids.getValue(node)
                appendLine("  n$index [label=\"${label(node)}\"];")
            }
            edges.forEach { (source, target) -> appendLine("  n$source -> n$target;") }
            appendLine("}")
        }
    }

    private fun StringBuilder.appendAttributeBlock(target: String, attributes: Map<String, String>) {
        if (attributes.isEmpty()) return
        val formatted = attributes.entries.joinToString(", ") { (name, value) ->
            "$name=${formatAttributeValue(value)}"
        }
        appendLine("$target [$formatted];")
    }

    private fun StringBuilder.appendAttributeAssignments(indent: String, attributes: Map<String, String>) {
        attributes.forEach { (name, value) ->
            appendLine("$indent$name=${formatAttributeValue(value)};")
        }
    }

    private fun formatAttributeValue(value: String): String =
        if (value.matches(unquotedAttribute)) value else "\"${escape(value)}\""

    private val unquotedAttribute = Regex("[A-Za-z_][A-Za-z0-9_]*|-?(?:\\d+(?:\\.\\d*)?|\\.\\d+)")

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n")

    /** Writes topology.dot and topology.png into [directory]. */
    fun render(topology: Topology, directory: Path) {
        Files.createDirectories(directory)
        val dot = directory.resolve("topology.dot")
        val png = directory.resolve("topology.png")
        val log = directory.resolve("topology-graphviz.log")
        Files.writeString(dot, toDot(topology))
        val process = try {
            ProcessBuilder(executable, "-Tpng", dot.toAbsolutePath().toString(), "-o", png.toAbsolutePath().toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start()
        } catch (error: java.io.IOException) {
            throw IllegalStateException("Install Graphviz and put dot on PATH, or set GRAPHVIZ_DOT to its executable", error)
        }
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            error("Graphviz timed out rendering $dot")
        }
        check(process.exitValue() == 0) { "Graphviz failed: ${Files.readString(log)}" }
        check(Files.isRegularFile(png) && Files.size(png) > 0) { "Graphviz did not produce $png" }
    }
}
