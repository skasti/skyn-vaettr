package no.skasti.skynvaettr.topology

import java.util.Collections
import java.util.IdentityHashMap

/** DSL builder for a reusable group of nodes and its exported port boundary. */
class GroupBuilder internal constructor(
    private val name: String,
) {
    private val nodes = mutableListOf<Node>()
    private val exposedPorts = linkedMapOf<String, Port<*>>()

    init {
        require(name.isNotBlank()) { "topology group name must not be blank" }
    }

    /** Adds a node to the group and returns it for wiring. */
    fun <T : Node> add(node: T): T {
        require(nodes.none { it === node }) { "the same node cannot appear twice in a topology group" }
        nodes += node
        return node
    }

    /** Exposes an internal port as part of the group's named boundary. */
    fun <T> expose(name: String, port: Port<T>): Port<T> {
        require(name.isNotBlank()) { "exposed port name must not be blank" }
        require(exposedPorts.put(name, port) == null) {
            "exposed port name '$name' is already registered"
        }
        return port
    }

    internal fun build(): Group {
        require(nodes.isNotEmpty()) { "topology group '$name' must contain nodes" }
        val ownedPorts = Collections.newSetFromMap(IdentityHashMap<Port<*>, Boolean>())
        nodes.forEach { node ->
            node.ports.forEach { port ->
                require(ownedPorts.add(port)) {
                    "port '${port.name}' belongs to multiple nodes in topology group '$name'"
                }
            }
        }
        exposedPorts.forEach { (portName, port) ->
            require(port in ownedPorts) {
                "exposed port '$portName' does not belong to a node in topology group '$name'"
            }
        }
        return BuiltGroup(name, nodes.toList(), exposedPorts.toMap())
    }

    private class BuiltGroup(
        override val name: String,
        override val nodes: List<Node>,
        override val exposedPorts: Map<String, Port<*>>,
    ) : Group {
        override fun toString(): String = name
    }
}
