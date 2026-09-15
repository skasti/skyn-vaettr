package no.skasti.skynvaettr.training

import no.skasti.skynvaettr.models.PredictionDecoder
import no.skasti.skynvaettr.models.TrainableModel
import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.runtime.ProcessingGraph
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.Signal
import no.skasti.skynvaettr.signals.SignalId

/**
 * Self-supervised objective that learns from meaningful transitions rather than every sense cycle.
 *
 * Whenever any Double signal makes a meaningful transition, the current graph executions become a
 * candidate source context. When a target signal later makes its own meaningful transition, the
 * model for that target is trained from the most recent transition context to the newly observed
 * value. This allows relationships such as dimmer-change -> later light-change to become training
 * experiences without configuring either signal as a cause or target.
 *
 * This is an incremental bridge for the current per-signal scalar prediction heads. A future general
 * transition head may additionally predict which signal transitions next and the elapsed-time
 * distribution.
 */
class TransitionTrainer(
    private val objectiveWeights: AdaptiveObjectiveWeights = AdaptiveObjectiveWeights(),
    private val detector: NumericTransitionDetector = NumericTransitionDetector(),
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

    var detectedTransitionCount: Long = 0
        private set

    var trainingExampleCount: Long = 0
        private set

    override fun onSenseCompleted(
        samples: List<Sample<*>>,
        graph: ProcessingGraph,
    ) {
        val numericSamples = samples
            .filter { it.value is Double }
            .sortedBy { it.timestamp }
            .map { sample ->
                @Suppress("UNCHECKED_CAST")
                val signal = sample.signal as Signal<Double>
                Sample(signal, sample.value as Double, sample.timestamp)
            }

        val transitions = numericSamples.mapNotNull(detector::observe)
        if (transitions.isEmpty()) return
        detectedTransitionCount += transitions.size

        transitions.forEach { transition ->
            val signalId = transition.current.signal.id
            pending.toMap().forEach { (key, previous) ->
                if (key.signalId != signalId) return@forEach

                objectiveWeights.record(
                    signalId = signalId,
                    objective = LearningObjective.Transition,
                    prediction = previous.prediction,
                    baseline = previous.baseline,
                    observed = transition.current.value,
                )
                previous.model.train(
                    previous.input,
                    previous.decoder.trainingTarget(transition.current.value),
                    weight = objectiveWeights.weight(signalId, LearningObjective.Transition),
                )
                trainingExampleCount++
                pending.remove(key)
            }
        }

        val latestNumeric = numericSamples
            .groupBy { it.signal.id }
            .mapValues { (_, signalSamples) -> signalSamples.maxBy { it.timestamp } }

        graph.predictionExecutions().forEach { execution ->
            val model = execution.model as? TrainableModel ?: return@forEach
            val predictionValue = execution.prediction.value as? Double ?: return@forEach
            val baseline = latestNumeric[execution.prediction.signal.id]?.value ?: return@forEach
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
                baseline = baseline,
            )
        }
    }
}
