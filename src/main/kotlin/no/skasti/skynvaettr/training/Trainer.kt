package no.skasti.skynvaettr.training

import no.skasti.skynvaettr.signals.Sample

/**
 * Independent training controller contract for a running Skynvættr.
 *
 * A Trainer owns its training policy: when to train, which model instances to train, how to sample
 * experience, and whether an update should trigger any work. It is not currently attached to
 * [no.skasti.skynvaettr.Vaettr].
 */
fun interface Trainer {
    fun onSenseCompleted(samples: List<Sample<*>>)
}
