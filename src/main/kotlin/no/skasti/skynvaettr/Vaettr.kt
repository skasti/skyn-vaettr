package no.skasti.skynvaettr

import no.skasti.skynvaettr.runtime.ProcessingGraph
import no.skasti.skynvaettr.runtime.SensingProcessingGraph
import no.skasti.skynvaettr.signals.Sample

/**
 * One running Skynvættr instance.
 *
 * [Vaettr] is the stable external entry point. World adapters and experiments feed observations into
 * [sense], while the configured [ProcessingGraph] owns how those observations propagate internally.
 *
 * The default graph is [SensingProcessingGraph], which provides the current best-known generic
 * sensory input representation while experiments remain free to replace the entire graph.
 */
class Vaettr(
    private val graph: ProcessingGraph = SensingProcessingGraph(),
) {
    fun sense(samples: List<Sample<*>>) {
        if (samples.isEmpty()) return
        graph.sense(samples)
    }

    fun sense(sample: Sample<*>) = sense(listOf(sample))
}
