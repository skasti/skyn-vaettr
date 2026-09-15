package no.skasti.skynvaettr.training

import no.skasti.skynvaettr.runtime.ProcessingGraph
import no.skasti.skynvaettr.signals.Sample

/**
 * Training controller attached to a running Skynvættr.
 *
 * A Trainer owns its training policy: when to train, which model instances to train, how to sample
 * experience, and whether a completed sense cycle should trigger any work at all. The runtime only
 * notifies trainers after observations have been committed to the canonical SampleStore and the
 * processing graph has completed the synchronous work for that cycle. The completed [graph] is
 * supplied so trainers can discover trainable model instances and their executions from the runtime
 * topology instead of receiving separately wired model references.
 */
fun interface Trainer {
    fun onSenseCompleted(
        samples: List<Sample<*>>,
        graph: ProcessingGraph,
    )
}
