package no.skasti.skynvaettr.reporting

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.IdentityHashMap
import java.util.concurrent.TimeUnit
import no.skasti.skynvaettr.topology.Node
import no.skasti.skynvaettr.topology.Port
import no.skasti.skynvaettr.topology.Topology

/**
 * Exports a topology as a Mermaid flowchart, preserving its groups and individual port connections.
 * Connected target ports outside [Topology.nodes] are shown as data export endpoints.
 */
class TopologyMermaidRenderer(
    private val topology: Topology,
    private val executable: String = System.getenv("MERMAID_CLI") ?: "mmdc",
    private val puppeteerConfig: String? = System.getenv("MERMAID_PUPPETEER_CONFIG"),
) {
    private val mermaidSource by lazy { buildMermaid() }

    /** Captures the topology on the first text or image export. Connect collectors before exporting. */
    fun toMermaid(): String = mermaidSource

    fun toSvg(output: Path) = renderImage(output, "svg")

    fun toPng(output: Path) = renderImage(output, "png")

    private fun renderImage(output: Path, format: String) {
        require(output.fileName.toString().endsWith(".$format")) { "Expected a .$format output path" }
        val destination = output.toAbsolutePath()
        Files.createDirectories(destination.parent)
        val work = Files.createTempDirectory(destination.parent, ".mermaid-")
        try {
            val input = work.resolve("topology.mmd")
            val image = work.resolve("topology.$format")
            val log = work.resolve("mermaid.log")
            Files.writeString(input, toMermaid())
            val command = mutableListOf(
                executable, "--input", input.toString(), "--output", image.toString(),
                "--backgroundColor", "white", "--width", "1800",
            )
            if (format == "png") command += listOf("--scale", "4")
            puppeteerConfig?.let { command += listOf("--puppeteerConfigFile", it) }
            val process = try {
                ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start()
            } catch (error: IOException) {
                throw IOException("Could not start Mermaid CLI '$executable'. Set MERMAID_CLI or install mmdc.", error)
            }
            try {
                check(process.waitFor(90, TimeUnit.SECONDS)) { "Mermaid CLI timed out rendering $output" }
                check(process.exitValue() == 0) { "Mermaid CLI failed rendering $output: ${Files.readString(log)}" }
                check(Files.isRegularFile(image) && Files.size(image) > 0) { "Mermaid CLI did not write $output" }
                Files.move(image, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            } finally {
                if (process.isAlive) process.destroyForcibly()
            }
        } finally {
            work.toFile().deleteRecursively()
        }
    }

    private fun buildMermaid(): String {
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

        val groupsByNode = IdentityHashMap<Node, String>()
        topology.groups.forEach { (groupName, group) ->
            require(groupName.isNotBlank()) { "Topology group name must not be blank" }
            group.nodes.forEach { node ->
                require(ids.containsKey(node)) { "Topology group '$groupName' contains a node outside the topology" }
                require(groupsByNode.put(node, groupName) == null) {
                    "Node belongs to more than one topology group"
                }
            }
        }

        val externalIds = IdentityHashMap<Port<*>, String>()
        val externalPorts = mutableListOf<Port<*>>()
        val connections = linkedSetOf<PortConnection>()
        nodes.forEachIndexed { sourceIndex, node ->
            node.ports.forEach { port ->
                port.synapses.forEach { synapse ->
                    require(synapse.source === port) { "Outgoing synapse has a different source port" }
                    val targetId = owners[synapse.target]?.let { "n$it" }
                        ?: externalIds.getOrPut(synapse.target) {
                            "externalPort${externalPorts.size}".also { externalPorts += synapse.target }
                        }
                    connections += PortConnection(
                        sourceNode = sourceIndex,
                        sourcePort = port.name,
                        targetId = targetId,
                        targetPort = synapse.target.name,
                    )
                }
            }
        }

        return buildString {
            appendLine("flowchart LR")
            appendLine("""%%{init: {"layout": "elk", "flowchart": {"curve": "rounded"}}}%%""")
            val emitted = java.util.Collections.newSetFromMap(IdentityHashMap<Node, Boolean>())
            topology.groups.values.forEachIndexed { groupIndex, group ->
                appendLine("  subgraph group$groupIndex[\"${escape(group.name)}\"]")
                group.nodes.forEach { node ->
                    appendNode(node, ids.getValue(node))
                    emitted += node
                }
                appendLine("  end")
            }
            nodes.forEach { node ->
                if (emitted.add(node)) appendNode(node, ids.getValue(node))
            }
            externalPorts.forEach { port ->
                appendLine("  ${externalIds.getValue(port)}@{ shape: das, label: \"${escape(port.name)}\" }")
            }
            connections.forEach { connection ->
                val label = connection.sourcePort
                appendLine(
                    "  n${connection.sourceNode} -->|\"${escape(label)}\"| ${connection.targetId}",
                )
            }
            if (nodes.isNotEmpty()) {
                appendLine("  classDef topologyNode fill:#E4F3FF,stroke:#CFDCE8,stroke-width:1.4px,color:#0055A5")
                appendLine("  class ${nodes.indices.joinToString(",") { "n$it" }} topologyNode")
            }
            if (externalPorts.isNotEmpty()) {
                appendLine("  classDef externalPort fill:#D5F5F0,stroke:#45A99B,stroke-width:1.4px,color:#075E54")
                appendLine("  class ${externalPorts.joinToString(",") { externalIds.getValue(it) }} externalPort")
            }
            appendLine("  linkStyle default stroke:#888888,stroke-width:1.1px")
            topology.groups.values.forEachIndexed { groupIndex, _ ->
                appendLine("  style group$groupIndex fill:#FFFFFF,stroke:#C7D8E8,color:#0055A5")
            }
        }
    }

    private fun StringBuilder.appendNode(node: Node, id: Int) {
        appendLine("    n$id[\"${escape(node.name)}\"]")
    }

    private fun escape(value: String): String = value
        .replace("&", "#38;")
        .replace("\"", "#quot;")
        .replace("|", "#124;")
        .replace("<", "#60;")
        .replace(">", "#62;")
        .replace("\r", "")
        .replace("\n", "<br/>")

    private data class PortConnection(
        val sourceNode: Int,
        val sourcePort: String,
        val targetId: String,
        val targetPort: String,
    )
}
