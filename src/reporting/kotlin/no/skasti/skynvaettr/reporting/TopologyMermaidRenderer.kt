package no.skasti.skynvaettr.reporting

import java.util.IdentityHashMap
import no.skasti.skynvaettr.topology.Node
import no.skasti.skynvaettr.topology.Port
import no.skasti.skynvaettr.topology.Topology

/**
 * Exports a topology as a Mermaid flowchart, preserving its groups and individual port connections.
 */
class TopologyMermaidRenderer {
    fun toMermaid(topology: Topology): String {
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

        val connections = linkedSetOf<PortConnection>()
        nodes.forEachIndexed { sourceIndex, node ->
            node.ports.forEach { port ->
                port.synapses.forEach { synapse ->
                    require(synapse.source === port) { "Outgoing synapse has a different source port" }
                    val targetIndex = requireNotNull(owners[synapse.target]) {
                        "Target port '${synapse.target.name}' is not owned by a node in this topology"
                    }
                    connections += PortConnection(
                        sourceNode = sourceIndex,
                        sourcePort = port.name,
                        targetNode = targetIndex,
                        targetPort = synapse.target.name,
                    )
                }
            }
        }

        return buildString {
            appendLine("flowchart LR")
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
            connections.forEach { connection ->
                val label = "${connection.sourcePort} → ${connection.targetPort}"
                appendLine(
                    "  n${connection.sourceNode} -->|\"${escape(label)}\"| n${connection.targetNode}",
                )
            }
            if (nodes.isNotEmpty()) {
                appendLine("  classDef topologyNode fill:#E4F3FF,stroke:#CFDCE8,stroke-width:1.4px,color:#0055A5")
                appendLine("  class ${nodes.indices.joinToString(",") { "n$it" }} topologyNode")
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
        val targetNode: Int,
        val targetPort: String,
    )
}
