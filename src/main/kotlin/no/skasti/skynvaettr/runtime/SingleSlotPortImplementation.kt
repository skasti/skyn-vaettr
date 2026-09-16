package no.skasti.skynvaettr.runtime

import no.skasti.skynvaettr.representation.Representation

/**
 * A named endpoint owned by a processing node.
 *
 * A port can emit representations through its outgoing synapses, receive representations from an
 * incoming synapse, and notify subscribers after its input state changes.
 */
interface Port {
    val name: String
    val pending: Representation?
    val onReceive: ReceiveEvent

    fun emit(value: Representation)

    fun receive(value: Representation)

    fun clear()

    fun connectTo(target: Port): Synapse
}

/** A subscription to a [ReceiveEvent]. */
fun interface Subscription {
    fun unsubscribe()
}

/** Event published after a [Port] has received and retained a representation. */
class ReceiveEvent {
    private val listeners = mutableSetOf<(Port) -> Unit>()

    fun subscribe(listener: (Port) -> Unit): Subscription {
        listeners += listener
        return Subscription { listeners -= listener }
    }

    internal fun publish(port: Port) {
        listeners.toList().forEach { listener -> listener(port) }
    }
}

/**
 * A port that holds exactly one unprocessed representation.
 *
 * A second received representation fails until [clear] is called. This deliberately prevents
 * unprocessed input from being overwritten silently.
 */
class SingleSlotPort(
    override val name: String,
) : Port {
    private val outgoing = mutableListOf<Synapse>()

    override var pending: Representation? = null
        private set

    override val onReceive = ReceiveEvent()

    override fun emit(value: Representation) {
        outgoing.toList().forEach { synapse -> synapse.deliver(value) }
    }

    override fun receive(value: Representation) {
        check(pending == null) {
            "Port '$name' already contains an unprocessed representation"
        }

        pending = value
        onReceive.publish(this)
    }

    override fun clear() {
        pending = null
    }

    override fun connectTo(target: Port): Synapse =
        Synapse(source = this, target = target).also(outgoing::add)
}
