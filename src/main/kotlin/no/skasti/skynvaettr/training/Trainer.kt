package no.skasti.skynvaettr.training

import no.skasti.skynvaettr.signals.Sample

/**
 * Training controller attached to a running Skynvættr.
 *
 * A Trainer owns its training policy: when to train, which model instances to train, how to sample
 * experience, and whether a completed sense cycle should trigger any work at all. The runtime only
 * notifies trainers after observations have been committed to the canonical SampleStore and the
 * processing graph has completed the synchronous work for that cycle.
 */
fun interface Trainer {
    fun onSenseCompleted(samples: List<Sample<*>>)
}
