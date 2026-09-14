package no.skasti.skynvaettr

import no.skasti.skynvaettr.runtime.ProcessingGraph
import no.skasti.skynvaettr.signals.Sample

/**
 * One running Skynvættr instance.
 *
 * [Vaettr] is the stable external entry point. World adapters and experiments feed observations into
 * [sense], while the configured [ProcessingGraph] owns how those observations propagate internally.
 */
class Vaettr(
    private val graph: ProcessingGraph,
) {
    fun sense(samples: List<Sample<*>>) {
        if (samples.isEmpty()) return
        graph.sense(samples)
    }

    fun sense(sample: Sample<*>) = sense(listOf(sample))
}
