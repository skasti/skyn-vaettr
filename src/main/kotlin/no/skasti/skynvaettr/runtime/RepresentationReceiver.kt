package no.skasti.skynvaettr.runtime

import no.skasti.skynvaettr.representation.Representation

/** Stateful input port for a [Node]. */
interface RepresentationReceiver {
    val owner: Node
    val name: String
    val pending: Representation?

    fun receive(value: Representation)

    fun clear()
}

/**
 * A receiver that holds exactly one unprocessed representation.
 *
 * Receiving a second representation before [clear] fails rather than silently replacing the
 * pending value.
 */
class SingleSlotReceiver(
    override val owner: Node,
    override val name: String,
) : RepresentationReceiver {
    override var pending: Representation? = null
        private set

    override fun receive(value: Representation) {
        check(pending == null) {
            "Receiver '$name' already contains an unprocessed representation"
        }

        pending = value
        owner.onInputReceived(this)
    }

    override fun clear() {
        pending = null
    }
}
