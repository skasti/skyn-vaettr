package no.skasti.skynvaettr.topology

/**
 * A named collection of nodes that forms one system inside a [Topology].
 *
 * A group may expose selected ports from its internal nodes as named boundary ports. The exposed
 * port is still owned by the internal node; the group only provides a convenient boundary for
 * constructing connections.
 */
interface Group {
    val name: String
    val nodes: List<Node>

    /** Ports selected as the public boundary of this group. */
    val exposedPorts: Map<String, Port<*>>
        get() = emptyMap()

    /** Returns an exposed port by name with the type inferred from the connection site. */
    fun <T: Any> port(name: String): Port<T> {
        val exposed = exposedPorts[name]
            ?: error("Topology group '$this' does not expose a port named '$name'")
        @Suppress("UNCHECKED_CAST")
        return exposed as Port<T>
    }

    /** Bracket form of [port] for concise topology wiring. */
    operator fun <T: Any> get(name: String): Port<T> = port(name)

    companion object {
        /** Builds a named group from nodes and exposed ports declared in [configure]. */
        fun build(name: String, configure: GroupBuilder.() -> Unit): Group =
            GroupBuilder(name).apply(configure).build()

        /** Builds a group with the default name `Group`. */
        fun build(configure: GroupBuilder.() -> Unit): Group =
            build("Group", configure)
    }
}
