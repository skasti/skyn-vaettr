package no.skasti.skynvaettr.training

import no.skasti.skynvaettr.models.PredictionDecoder
import no.skasti.skynvaettr.models.TrainableModel
import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.runtime.ProcessingGraph
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.SignalId

/**
 * Self-supervised online trainer that learns from ordinary observed transitions.
 *
 * Each prediction execution from one sense cycle is retained until the same signal is observed in a
 * later cycle. The later observed value becomes the training target for the exact model/input pair
 * that produced the earlier prediction. No expectation needs to be opened or resolved first.
 *
 * The elapsed time is deliberately not fixed here: it is whatever interval actually occurred between
 * observations. Temporal context remains part of the graph-produced Representation, so models can
 * learn from observation sequences without introducing a configured +N prediction horizon.
 */
class ObservationTrainer : Trainer {
    private data class Pending(
        val model: TrainableModel,
        val decoder: PredictionDecoder<Any?>,
        val input: Representation,
    )

    private data class Key(
        val modelIdentity: Int,
        val signalId: SignalId,
    )

    private val pending = linkedMapOf<Key, Pending>()

    var trainingExampleCount: Long = 0
        private set

    override fun onSenseCompleted(
        samples: List<Sample<*>>,
        graph: ProcessingGraph,
    ) {
        val latestSamples = samples
            .groupBy { it.signal.id }
            .mapValues { (_, signalSamples) -> signalSamples.maxBy { it.timestamp } }

        pending.toMap().forEach { (key, previous) ->
            val observed = latestSamples[key.signalId] ?: return@forEach
            previous.model.train(
                previous.input,
                previous.decoder.trainingTarget(observed.value),
            )
            trainingExampleCount++
            pending.remove(key)
        }

        graph.predictionExecutions().forEach { execution ->
            val model = execution.model as? TrainableModel ?: return@forEach
            @Suppress("UNCHECKED_CAST")
            val decoder = execution.decoder as PredictionDecoder<Any?>
            val key = Key(
                modelIdentity = System.identityHashCode(model),
                signalId = execution.prediction.signal.id,
            )
            pending[key] = Pending(
                model = model,
                decoder = decoder,
                input = execution.input,
            )
        }
    }
}
