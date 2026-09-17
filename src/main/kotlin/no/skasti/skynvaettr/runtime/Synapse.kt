package no.skasti.skynvaettr.runtime

/**
 * A directed topology connection from one [source] port to one [target] port.
 *
 * A source port may own several synapses, allowing one emitted representation to fan out to
 * multiple target ports.
 */
class Synapse<T>(
    val source: Port<T>,
    val target: Port<T>,
) {
    fun deliver(value: T) {
        target.receive(value)
    }
}
