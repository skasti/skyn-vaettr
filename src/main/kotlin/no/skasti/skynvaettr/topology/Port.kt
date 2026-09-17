package no.skasti.skynvaettr.topology

import no.skasti.skynvaettr.runtime.ReceiveEvent

/**
 * A named endpoint owned by a processing node.
 *
 * A port can emit representations through its outgoing synapses, receive representations from an
 * incoming synapse, and notify subscribers after its input state changes.
 */
interface Port<T> {
    val name: String
    val onReceive: ReceiveEvent

    fun emit(value: T)

    fun receive(value: T)

    fun connectTo(target: Port<T>): Synapse<T>
}
