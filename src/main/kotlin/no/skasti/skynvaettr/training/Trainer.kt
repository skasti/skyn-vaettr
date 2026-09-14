package no.skasti.skynvaettr.training

import no.skasti.skynvaettr.signals.Sample

/**
 * Training controller attached to a running Skynvættr.
 *
 * A Trainer owns its training policy: when to train, which model instances to train, how to sample
 * experience, and whether a newly stored sensory batch should trigger any work at all. The runtime
 * only notifies trainers after observations have been committed to the canonical SampleStore.
 */
fun interface Trainer {
    fun onSamplesStored(samples: List<Sample<*>>)
}
