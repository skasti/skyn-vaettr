package no.skasti.skynvaettr.episodes

import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import no.skasti.skynvaettr.signals.Signal

/**
 * Records bounded episodes around material changes in a model evaluation score.
 *
 * The recorder deliberately reacts to changes in evaluation quality rather than to the absolute
 * score. A model that remains consistently wrong therefore does not create one unbounded episode;
 * an episode starts only when the evaluation changes materially, such as wrong -> right or
 * right -> wrong.
 *
 * [preRoll] includes signal history from before the triggering evaluation change. Each later
 * material change extends the episode's quiet/post-roll window. The episode closes only after
 * [postRoll] has elapsed without another material change and [minimumEpisodeDuration] is satisfied.
 *
 * The score itself is intentionally domain-agnostic. Callers may provide correctness, error,
 * probability loss, or another scalar evaluation as long as larger numeric differences mean a
 * materially changed evaluation according to [minimumEvaluationChange].
 */
class EvaluationChangeEpisodeRecorder(
    signals: List<Signal<*>>,
    val preRoll: Duration = Duration.ZERO,
    val postRoll: Duration,
    val minimumEpisodeDuration: Duration = Duration.ZERO,
    val minimumEvaluationChange: Double = 0.0,
) {
    val signals: List<Signal<*>> = signals.toList()

    private data class ActiveEpisode(
        val from: Instant,
        var lastTriggerAt: Instant,
    )

    private var previousEvaluation: Double? = null
    private var lastObservedAt: Instant? = null
    private var activeEpisode: ActiveEpisode? = null

    init {
        require(this.signals.isNotEmpty()) { "Episode recorder must contain at least one signal" }
        require(this.signals.distinct().size == this.signals.size) {
            "Episode recorder signals must be unique"
        }
        require(!preRoll.isNegative) { "preRoll must not be negative" }
        require(!postRoll.isNegative) { "postRoll must not be negative" }
        require(!minimumEpisodeDuration.isNegative) { "minimumEpisodeDuration must not be negative" }
        require(minimumEvaluationChange.isFinite() && minimumEvaluationChange >= 0.0) {
            "minimumEvaluationChange must be finite and non-negative"
        }
    }

    /** Whether an episode is currently open. */
    val isRecording: Boolean
        get() = activeEpisode != null

    /**
     * Observes one scalar model evaluation.
     *
     * Returns any episode that became complete at this observation. A single call can close an old
     * episode and start a new one when a new material change arrives after the old quiet window.
     */
    fun observe(
        at: Instant,
        evaluation: Double,
    ): List<EpisodeDefinition> {
        require(evaluation.isFinite()) { "evaluation must be finite" }
        requireMonotonic(at)

        val previous = previousEvaluation
        val changed = previous != null && abs(evaluation - previous) > minimumEvaluationChange
        val completed = mutableListOf<EpisodeDefinition>()

        activeEpisode?.let { active ->
            val deadline = closeAt(active)
            if (at.isAfter(deadline) || (at == deadline && !changed)) {
                completed += definition(active)
                activeEpisode = null
            }
        }

        if (changed) {
            val active = activeEpisode
            if (active == null) {
                activeEpisode = ActiveEpisode(
                    from = at.minus(preRoll),
                    lastTriggerAt = at,
                )
            } else {
                active.lastTriggerAt = at
            }
        }

        previousEvaluation = evaluation
        lastObservedAt = at
        return completed
    }

    /**
     * Advances time without a new evaluation, closing an episode when its quiet window has elapsed.
     */
    fun advanceTo(at: Instant): List<EpisodeDefinition> {
        requireMonotonic(at)
        val active = activeEpisode ?: run {
            lastObservedAt = at
            return emptyList()
        }

        val completed = if (!at.isBefore(closeAt(active))) {
            activeEpisode = null
            listOf(definition(active))
        } else {
            emptyList()
        }
        lastObservedAt = at
        return completed
    }

    /** Clears temporal state while keeping recorder configuration. */
    fun reset() {
        previousEvaluation = null
        lastObservedAt = null
        activeEpisode = null
    }

    private fun closeAt(active: ActiveEpisode): Instant {
        val quietDeadline = active.lastTriggerAt.plus(postRoll)
        val minimumDeadline = active.from.plus(minimumEpisodeDuration)
        return if (quietDeadline.isAfter(minimumDeadline)) quietDeadline else minimumDeadline
    }

    private fun definition(active: ActiveEpisode): EpisodeDefinition =
        EpisodeDefinition(
            from = active.from,
            to = closeAt(active),
            signals = signals,
        )

    private fun requireMonotonic(at: Instant) {
        val previous = lastObservedAt
        require(previous == null || !at.isBefore(previous)) {
            "Episode recorder observations must be ordered by timestamp"
        }
    }
}
