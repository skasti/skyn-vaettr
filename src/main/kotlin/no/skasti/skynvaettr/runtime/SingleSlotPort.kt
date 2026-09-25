package no.skasti.skynvaettr.runtime

import no.skasti.skynvaettr.topology.Port
import no.skasti.skynvaettr.topology.Synapse

/** A subscription to a [ReceiveEvent]. */
fun interface Subscription {
    fun unsubscribe()
}

/** Event published after a [no.skasti.skynvaettr.topology.Port] has received and retained a representation. */
class ReceiveEvent {
    private val listeners = mutableSetOf<(Port<*>) -> Unit>()

    fun subscribe(listener: (Port<*>) -> Unit): Subscription {
        listeners += listener
        return Subscription { listeners -= listener }
    }

    operator fun plusAssign(listener: (Port<*>) -> Unit) {
        listeners += listener
    }

    internal fun publish(port: Port<*>) {
        listeners.toList().forEach { listener -> listener(port) }
    }
}

/**
 * A port that holds exactly one unprocessed representation.
 *
 * A second received representation fails until [clear] is called. This deliberately prevents
 * unprocessed input from being overwritten silently.
 */
class SingleSlotPort<T: Any>(
    override val name: String,
) : Port<T> {
    private val connections = mutableListOf<Synapse<T>>()
    override val synapses: List<Synapse<T>>
        get() = connections.toList()

    var pending: T? = null
        private set

    override val onReceive = ReceiveEvent()

    override fun emit(value: T) {
        connections.toList().forEach { synapse -> synapse.deliver(value) }
    }

    override fun receive(value: T) {
        check(pending == null) {
            "Port '$name' already contains an unprocessed representation"
        }

        pending = value
        onReceive.publish(this)
    }

    fun clear() {
        pending = null
    }

    override fun connectTo(vararg targets: Port<T>) {
        require(targets.isNotEmpty()) { "connectTo requires at least one target" }
        targets.forEach { target ->
            if (connections.all { it.target !== target }) {
                Synapse(source = this, target = target).also(connections::add)
            }
        }
    }
}
