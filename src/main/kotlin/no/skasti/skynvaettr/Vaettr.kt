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
 * received sample is committed to [sampleStore] before the processing graph and trainers are
 * notified, so online processing and later learning share one historical source of truth.
 */
class Vaettr(
    val sampleStore: MutableSampleStore = InMemorySampleStore(),
    graph: ProcessingGraph? = null,
    private val trainers: List<Trainer> = emptyList(),
) {
    private val graph: ProcessingGraph = graph ?: SensingProcessingGraph(sampleStore)

    constructor(graph: ProcessingGraph) : this(graph = graph)

    fun sense(samples: List<Sample<*>>) {
        if (samples.isEmpty()) return

        sampleStore.append(samples)
        graph.sense(samples)
        trainers.forEach { it.onSamplesStored(samples) }
    }

    fun sense(sample: Sample<*>) = sense(listOf(sample))
}
