package no.skasti.skynvaettr.training

import java.time.Duration
import java.time.Instant
import no.skasti.skynvaettr.expectations.Prediction
import no.skasti.skynvaettr.models.LearnedAttentionTransitionModel
import no.skasti.skynvaettr.models.TransitionPredictionDecoder
import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.runtime.ProcessingGraph
import no.skasti.skynvaettr.runtime.SensingProcessingGraph
import no.skasti.skynvaettr.runtime.SensoryPosition
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.Signal
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
 * Trains a shared learnable attention model to predict the next meaningful numeric transition.
 *
 * Predictions are event-conditioned and have no target timestamp. A prediction made immediately
 * after one transition remains pending until the next meaningful transition is observed; that
 * observed transition then supplies both target signal identity and target value for training.
 */
class TransitionPredictionTrainer(
    val model: LearnedAttentionTransitionModel,
    val decoder: TransitionPredictionDecoder,
    private val detector: NumericTransitionDetector = NumericTransitionDetector(),
) : Trainer {
    private data class Pending(
        val formedAt: Instant,
        val sourceTransition: SignalId,
        val input: Representation,
        val positions: List<SensoryPosition>,
        val prediction: Prediction<Double>,
        val attentionWeights: DoubleArray,
    )

    private var pending: Pending? = null
    private val mutableRecords = mutableListOf<TransitionPredictionRecord>()

    val records: List<TransitionPredictionRecord>
        get() = mutableRecords.toList()

    override fun onSenseCompleted(samples: List<Sample<*>>, graph: ProcessingGraph) {
        val sensingGraph = graph as? SensingProcessingGraph ?: return
        val numericSamples = samples.mapNotNull { sample ->
            val value = sample.value as? Double ?: return@mapNotNull null
            @Suppress("UNCHECKED_CAST")
            val signal = sample.signal as Signal<Double>
            decoder.observe(signal)
            Sample(signal, value, sample.timestamp)
        }
        val transitions = numericSamples
            .mapNotNull(detector::observe)
            .sortedWith(compareBy<NumericTransition>({ it.current.timestamp }, { it.current.signal.id.value }))
        if (transitions.isEmpty()) return

        // One model output represents one next transition. Simultaneous transitions are currently
        // resolved deterministically by signal id; richer set-valued event targets can follow later.
        val outcome = transitions.first()
        pending?.let { previous ->
            val beforeLoss = model.exponentialMovingLoss
            model.train(
                input = previous.input,
                target = decoder.trainingTarget(outcome.current.signal, outcome.current.value),
            )
            mutableRecords += TransitionPredictionRecord(
                formedAt = previous.formedAt,
                sourceTransition = previous.sourceTransition,
                prediction = previous.prediction,
                resolvedAt = outcome.current.timestamp,
                actualSignal = outcome.current.signal.id,
                actualValue = outcome.current.value,
                attention = previous.positions.zip(previous.attentionWeights.asIterable()).map { (position, weight) ->
                    AttentionAttribution(
                        signalId = position.signalId,
                        age = Duration.between(position.timestamp, previous.formedAt).coerceAtLeast(Duration.ZERO),
                        weight = weight,
                    )
                },
                trainingLoss = beforeLoss,
            )
        }

        val input = sensingGraph.latestRepresentation ?: return
        val positions = sensingGraph.latestPositions
        val output = model.forward(input)
        val prediction = decoder.decode(output)
        pending = Pending(
            formedAt = outcome.current.timestamp,
            sourceTransition = outcome.current.signal.id,
            input = input,
            positions = positions,
            prediction = prediction,
            attentionWeights = model.latestAttentionWeights(),
        )
    }

    fun accuracy(records: List<TransitionPredictionRecord> = this.records): Double =
        if (records.isEmpty()) 0.0 else records.count(TransitionPredictionRecord::signalCorrect).toDouble() / records.size
}
