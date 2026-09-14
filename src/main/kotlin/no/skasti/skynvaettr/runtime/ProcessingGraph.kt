package no.skasti.skynvaettr.runtime

import no.skasti.skynvaettr.signals.Sample

/**
 * Internal processing topology used by a running Skynvættr.
 *
 * The contract is intentionally small. Implementations may be linear pipelines or arbitrary directed
 * graphs with fan-out, feedback, stateful components, independent update schedules, and learned
 * modules. Those execution semantics are deliberately not prescribed here yet.
 */
fun interface ProcessingGraph {
    fun sense(samples: List<Sample<*>>)
}
