package no.skasti.skynvaettr.training

import kotlin.math.abs
import no.skasti.skynvaettr.models.PredictionDecoder
import no.skasti.skynvaettr.models.TrainableModel
import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.runtime.ProcessingGraph
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.SignalId

/**
 * Continuous self-supervised objective for ordinary numeric evolution.
 *
 * The previous execution is evaluated when the same signal is observed again. Exact plateaus are
 * deliberately not added as training examples: persistence already predicts them perfectly and they
 * would otherwise swamp sparse but informative dynamics such as delayed dimmer/light transitions.
 *
 * Training weight is supplied by [AdaptiveObjectiveWeights], shared with other objectives such as
 * [TransitionTrainer]. Objective usefulness is scored relative to a persistence baseline rather than
 * by raw accuracy, so an objective receives credit only when it predicts change better than "stay at
 * the previous value".
 */
class ObservationTrainer(
    private val objectiveWeights: AdaptiveObjectiveWeights = AdaptiveObjectiveWeights(),
    private val minimumChange: Double = 1e-12,
) : Trainer {
    private data class Pending(
        val model: TrainableModel,
        val decoder: PredictionDecoder<Any?>,
        val input: Representation,
        val prediction: Double,
        val baseline: Double,
    )

    private data class Key(
        val modelIdentity: Int,
        val signalId: SignalId,
    )

    private val pending = linkedMapOf<Key, Pending>()

    init {
        require(minimumChange >= 0.0)
    }

    var trainingExampleCount: Long = 0
        private set

    override fun onSenseCompleted(
        samples: List<Sample<*>>,
        graph: ProcessingGraph,
    ) {
        val latestSamples = samples
            .filter { it.value is Double }
            .groupBy { it.signal.id }
            .mapValues { (_, signalSamples) -> signalSamples.maxBy { it.timestamp } }

        pending.toMap().forEach { (key, previous) ->
            val observedSample = latestSamples[key.signalId] ?: return@forEach
            val observed = observedSample.value as Double
            if (abs(observed - previous.baseline) > minimumChange) {
                objectiveWeights.record(
                    signalId = key.signalId,
                    objective = LearningObjective.Continuous,
                    prediction = previous.prediction,
                    baseline = previous.baseline,
                    observed = observed,
                )
                previous.model.train(
                    previous.input,
                    previous.decoder.trainingTarget(observed),
                    weight = objectiveWeights.weight(key.signalId, LearningObjective.Continuous),
                )
                trainingExampleCount++
            }
            pending.remove(key)
        }

        graph.predictionExecutions().forEach { execution ->
            val model = execution.model as? TrainableModel ?: return@forEach
            val predictionValue = execution.prediction.value as? Double ?: return@forEach
            val current = latestSamples[execution.prediction.signal.id]?.value as? Double ?: return@forEach
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
                prediction = predictionValue,
                baseline = current,
            )
        }
    }
}
