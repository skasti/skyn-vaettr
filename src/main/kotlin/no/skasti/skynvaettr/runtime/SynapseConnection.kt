package no.skasti.skynvaettr.runtime

import no.skasti.skynvaettr.representation.Representation

/**
 * A directed topology connection from one [source] port to one [target] port.
 *
 * A source port may own several synapses, allowing one emitted representation to fan out to
 * multiple target ports.
 */
class Synapse(
    val source: Port,
    val target: Port,
) {
    fun deliver(value: Representation) {
        target.receive(value)
    }
}
