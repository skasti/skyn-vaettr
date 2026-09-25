package no.skasti.skynvaettr.topology

/**
 * A processing component that owns ports and subscribes to their receive events.
 *
 * A node decides whether an incoming representation should be processed immediately, retained until
 * other ports have values, accumulated, or handled according to another domain-specific policy.
 */
interface Node {
    val name: String
    val ports: List<Port<*>>

    /** Returns a named port with its type inferred from the connection site. */
    fun <T : Any> port(portName: String): Port<T> {
        val selected = ports.firstOrNull { it.name == portName }
            ?: error("Node '$name' does not expose a port named '$portName'")
        @Suppress("UNCHECKED_CAST")
        return selected as Port<T>
    }

    /** Bracket form of [port] for concise topology wiring. */
    operator fun <T : Any> get(name: String): Port<T> = port<T>(name)
}
