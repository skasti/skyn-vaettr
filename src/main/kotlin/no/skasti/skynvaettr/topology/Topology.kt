package no.skasti.skynvaettr.topology

/** A processing network responsible for advancing its nodes when [update] is called. */
interface Topology {
    val nodes: List<Node>

    /** Named systems used by topology-aware tools such as the Graphviz renderer. */
    val groups: Map<String, Group>
        get() = emptyMap()

    /** External inputs derived from the nodes in this topology. */
    val entryPoints: List<EntryPoint<*>>
        get() = nodes.filterIsInstance<EntryPoint<*>>()

    fun update()
}
