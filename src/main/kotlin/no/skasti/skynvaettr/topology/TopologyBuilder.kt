package no.skasti.skynvaettr.topology

import java.util.Collections
import java.util.IdentityHashMap

/** DSL builder for composing nodes into named systems in a [Topology]. */
class TopologyBuilder internal constructor() {
    private val components = mutableListOf<List<Node>>()
    private val groups = mutableListOf<Group>()

    /** Adds a named group and returns the same instance so its exposed ports can be connected. */
    fun <T : Group> add(group: T): T {
        require(group.name.isNotBlank()) { "topology group name must not be blank" }
        require(group.nodes.isNotEmpty()) { "topology group '${group.name}' must contain nodes" }
        require(groups.none { it.name == group.name }) {
            "topology group name '${group.name}' is already registered"
        }
        groups += group
        components += group.nodes
        return group
    }

    /** Adds a node outside a named group. */
    fun <T : Node> add(node: T): T {
        components += listOf(node)
        return node
    }

    internal fun build(): Definition {
        val nodes = components.flatten()
        val identities = Collections.newSetFromMap(IdentityHashMap<Node, Boolean>())
        nodes.forEach { node ->
            require(identities.add(node)) { "the same node cannot appear twice in a topology" }
        }
        return Definition(nodes = nodes, groups = groups.toList())
    }

    internal data class Definition(
        val nodes: List<Node>,
        val groups: List<Group>,
    )
}
