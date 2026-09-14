package no.skasti.skynvaettr

import no.skasti.skynvaettr.runtime.ProcessingGraph
import no.skasti.skynvaettr.runtime.SensingProcessingGraph
import no.skasti.skynvaettr.signals.InMemorySampleStore
import no.skasti.skynvaettr.signals.MutableSampleStore
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.training.Trainer

/**
 * One running Skynvættr instance.
 *
 * [sense] is the canonical observation ingress for both simulated and live environments. Every
 * received sample is committed to [sampleStore] before the processing graph runs. Trainers are
 * notified only after the graph has completed the synchronous work for the same sense cycle, so
 * they can treat one [sense] call as one completed observation round while reading historical
 * experience from the canonical store when they choose to train.
 */
class Vaettr(
    val sampleStore: MutableSampleStore = InMemorySampleStore(),
    graph: ProcessingGraph? = null,
    private val trainers: List<Trainer> = emptyList(),
) {
    private val graph: ProcessingGraph = graph ?: SensingProcessingGraph(sampleStore)

    fun sense(samples: List<Sample<*>>) {
        if (samples.isEmpty()) return

        sampleStore.append(samples)
        graph.sense(samples)
        trainers.forEach { it.onSenseCompleted(samples) }
    }

    fun sense(sample: Sample<*>) = sense(listOf(sample))
}
