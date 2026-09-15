package no.skasti.skynvaettr.training

import kotlin.math.abs
import no.skasti.skynvaettr.signals.SignalId

/** Self-supervised learning objectives that may contribute training signal to the same model. */
enum class LearningObjective {
    Continuous,
    Transition,
}

/**
 * Tracks relative predictive usefulness of self-supervised objectives per signal.
 *
 * Objectives are evaluated only when the observed target differs materially from a persistence
 * baseline. A score therefore measures whether the model improves on simply predicting that the
 * signal will remain unchanged; long plateaus do not make the continuous objective look artificially
 * excellent.
 *
 * The implementation deliberately remains model-agnostic. It exposes soft weights rather than
 * handing exclusive ownership of a signal to one objective, allowing both objectives to contribute
 * when both are useful in the current data.
 */
class AdaptiveObjectiveWeights(
    private val smoothing: Double = 0.15,
    private val minimumWeight: Double = 0.10,
    private val baselineChangeFloor: Double = 1e-9,
) {
    private data class Score(
        var movingSkill: Double = 0.0,
        var observations: Long = 0,
    )

    private val scores = mutableMapOf<Pair<SignalId, LearningObjective>, Score>()

    init {
        require(smoothing in 0.0..1.0)
        require(minimumWeight in 0.0..<0.5)
        require(baselineChangeFloor > 0.0)
    }

    /**
     * Records skill relative to persistence. Positive values mean the model beat "no change".
     * Targets where persistence is already essentially perfect are intentionally ignored.
     */
    fun record(
        signalId: SignalId,
        objective: LearningObjective,
        prediction: Double,
        baseline: Double,
        observed: Double,
    ) {
        val baselineError = abs(observed - baseline)
        if (baselineError <= baselineChangeFloor) return

        val modelError = abs(observed - prediction)
        val normalizedSkill = ((baselineError - modelError) / baselineError).coerceIn(-1.0, 1.0)
        val score = scores.getOrPut(signalId to objective, ::Score)
        score.movingSkill = if (score.observations == 0L) {
            normalizedSkill
        } else {
            (1.0 - smoothing) * score.movingSkill + smoothing * normalizedSkill
        }
        score.observations++
    }

    fun weight(signalId: SignalId, objective: LearningObjective): Double {
        val continuous = scores[signalId to LearningObjective.Continuous]
        val transition = scores[signalId to LearningObjective.Transition]

        if (continuous?.observations == 0L && transition?.observations == 0L) return 0.5
        if (continuous == null && transition == null) return 0.5

        val continuousScore = positiveScore(continuous)
        val transitionScore = positiveScore(transition)
        val total = continuousScore + transitionScore
        if (total <= 1e-12) return 0.5

        val continuousShare = continuousScore / total
        val boundedContinuous = minimumWeight + (1.0 - 2.0 * minimumWeight) * continuousShare
        return when (objective) {
            LearningObjective.Continuous -> boundedContinuous
            LearningObjective.Transition -> 1.0 - boundedContinuous
        }
    }

    fun skill(signalId: SignalId, objective: LearningObjective): Double? =
        scores[signalId to objective]?.takeIf { it.observations > 0 }?.movingSkill

    private fun positiveScore(score: Score?): Double =
        score?.takeIf { it.observations > 0 }?.movingSkill?.let { (it + 1.0) / 2.0 } ?: 0.5
}
