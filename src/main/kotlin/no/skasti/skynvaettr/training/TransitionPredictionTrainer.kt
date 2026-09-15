package no.skasti.skynvaettr.training

import java.time.Duration
import java.time.Instant
import no.skasti.skynvaettr.expectations.Prediction
import no.skasti.skynvaettr.runtime.ProcessingGraph
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

/** One candidate-query -> sensory-key attention cell, labeled only for diagnostics. */
data class AttentionAttribution(
    val queryIndex: Int,
    val querySignalId: SignalId,
    val keyIndex: Int,
    val keySignalId: SignalId,
    val keyAge: Duration,
    val weight: Double,
)

/**
 * Supervision for graph-produced next-transition predictions.
 *
 * Inference belongs entirely to [TransitionPredictionSource]. This trainer keeps the previous
 * completed execution pending; when the graph later produces another transition execution, that
 * observed transition becomes the training target for the exact previous model/input pair.
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
        val positions = execution.historyPositions
        require(execution.attentionWeights.size == execution.candidateSignals.size) {
            "Expected one attention row per candidate signal"
        }

        return buildList {
            execution.attentionWeights.forEachIndexed { queryIndex, row ->
                require(row.size == positions.size) { "Expected one attention weight per sensory-history position" }
                val querySignal = execution.candidateSignals[queryIndex]
                row.forEachIndexed { keyIndex, weight ->
                    val keyPosition = positions[keyIndex]
                    add(
                        AttentionAttribution(
                            queryIndex = queryIndex,
                            querySignalId = querySignal,
                            keyIndex = keyIndex,
                            keySignalId = keyPosition.signalId,
                            keyAge = Duration.between(keyPosition.timestamp, formedAt).coerceAtLeast(Duration.ZERO),
                            weight = weight,
                        ),
                    )
                }
            }
        }
    }
}
