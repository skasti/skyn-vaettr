package no.skasti.skynvaettr.training

import java.time.Duration
import java.time.Instant
import no.skasti.skynvaettr.expectations.Prediction
import no.skasti.skynvaettr.runtime.ProcessingGraph
import no.skasti.skynvaettr.runtime.SensoryPosition
import no.skasti.skynvaettr.runtime.TransitionPredictionExecution
import no.skasti.skynvaettr.runtime.TransitionPredictionSource
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.SignalId

/** One resolved horizon-free prediction of the next meaningful numeric transition. */
data class TransitionPredictionRecord(
    val formedAt: Instant,
    val sourceTransition: SignalId,
    val prediction: Prediction<Double>,
    val resolvedAt: Instant,
    val actualSignal: SignalId,
    val actualValue: Double,
    val attention: List<AttentionAttribution>,
    val trainingLoss: Double?,
) {
    val signalCorrect: Boolean
        get() = prediction.signal.id == actualSignal
}

data class AttentionAttribution(
    val signalId: SignalId,
    val age: Duration,
    val weight: Double,
)

/**
 * Supervision for graph-produced next-transition predictions.
 *
 * Inference belongs entirely to [TransitionPredictionSource]: the graph detects the meaningful
 * source transition, constructs Q/K/V input, runs the model and decodes a [Prediction]. This trainer
 * only keeps the previous completed execution pending. When the graph later produces another
 * transition execution, that transition is the observed target for the previous prediction and the
 * exact model/input pair that produced it is trained.
 */
class TransitionPredictionTrainer : Trainer {
    private var pending: TransitionPredictionExecution? = null
    private val mutableRecords = mutableListOf<TransitionPredictionRecord>()

    val records: List<TransitionPredictionRecord>
        get() = mutableRecords.toList()

    override fun onSenseCompleted(samples: List<Sample<*>>, graph: ProcessingGraph) {
        val source = graph as? TransitionPredictionSource ?: return
        val current = source.latestTransitionPredictionExecution() ?: return

        pending?.let { previous ->
            val outcome = current.sourceTransition.current
            val beforeLoss = previous.model.exponentialMovingLoss
            previous.model.train(
                input = previous.input,
                target = previous.decoder.trainingTarget(outcome.signal, outcome.value),
            )
            mutableRecords += TransitionPredictionRecord(
                formedAt = previous.sourceTransition.current.timestamp,
                sourceTransition = previous.sourceTransition.current.signal.id,
                prediction = previous.prediction,
                resolvedAt = outcome.timestamp,
                actualSignal = outcome.signal.id,
                actualValue = outcome.value,
                attention = attentionAttribution(previous),
                trainingLoss = beforeLoss,
            )
        }

        pending = current
    }

    fun accuracy(records: List<TransitionPredictionRecord> = this.records): Double =
        if (records.isEmpty()) 0.0 else records.count(TransitionPredictionRecord::signalCorrect).toDouble() / records.size

    private fun attentionAttribution(execution: TransitionPredictionExecution): List<AttentionAttribution> {
        val formedAt = execution.sourceTransition.current.timestamp
        return execution.historyPositions
            .zip(execution.attentionWeights.asIterable())
            .map { (position: SensoryPosition, weight: Double) ->
                AttentionAttribution(
                    signalId = position.signalId,
                    age = Duration.between(position.timestamp, formedAt).coerceAtLeast(Duration.ZERO),
                    weight = weight,
                )
            }
    }
}
