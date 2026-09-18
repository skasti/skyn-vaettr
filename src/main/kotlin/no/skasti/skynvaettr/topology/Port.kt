package no.skasti.skynvaettr.topology

import no.skasti.skynvaettr.runtime.ReceiveEvent

/**
 * A named endpoint owned by a processing node.
 *
 * A port can emit representations through its outgoing synapses, receive representations from an
 * incoming synapse, and notify subscribers after its input state changes.
 */
interface Port<T: Any> {
    val name: String
    /** Outgoing connections from this port, exposed for topology inspection. */
    val synapses: List<Synapse<T>>
    val onReceive: ReceiveEvent

    fun emit(value: T)

    fun receive(value: T)

    /** Connects this port as the source to one or more target ports. */
    fun connectTo(vararg targets: Port<T>)

    /** Connects this port as the target to one or more source ports. */
    fun receiveFrom(vararg sources: Port<T>) {
        require(sources.isNotEmpty()) { "receiveFrom requires at least one source" }
        sources.forEach { source -> source.connectTo(this) }
    }
}
