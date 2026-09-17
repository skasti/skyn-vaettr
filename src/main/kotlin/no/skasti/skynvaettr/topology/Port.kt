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

    /** Connects this port to one or more targets. */
    fun connectTo(vararg targets: Port<T>)
}
