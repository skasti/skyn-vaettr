package no.skasti.skynvaettr.training

import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import no.skasti.skynvaettr.episodes.EpisodeDefinition
import no.skasti.skynvaettr.expectations.Expectation
import no.skasti.skynvaettr.expectations.ExpectationPolicy
import no.skasti.skynvaettr.models.TrainablePredictionModel
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.SampleStore
import no.skasti.skynvaettr.signals.Signal

/**
 * Generic online trainer that turns resolved expectations into replayable episodes.
 *
 * The trainer does not impose a forecast horizon. It captures the model input when an expectation
 * is formed, lets [policy] decide when that belief has resolved, and trains the model toward the
 * value observed at that resolution. The resulting episode spans the expectation lifecycle (plus
 * optional preceding context) and can be selected for replay by any [ReplaySelector].
 */
class ExpectationTrainer<I, T>(
    private val sampleStore: SampleStore,
    private val targetSignal: Signal<T>,
    private val model: TrainablePredictionModel<I, T>,
    private val policy: ExpectationPolicy<T>,
    episodeSignals: List<Signal<*>>,
    private val inputAt: (SampleStore, Instant) -> I?,
    private val replaySelector: ReplaySelector<ExpectationExperience<I, T>> = ReplaySelector { null },
    private val episodeContextBefore: Duration = Duration.ZERO,
) : Trainer {
    private val episodeSignals = episodeSignals.toList()
    private val resolvedExpectations = mutableListOf<Expectation<T>>()
    private val resolvedExperiences = mutableListOf<ExpectationExperience<I, T>>()

    private var activeExpectation: Expectation<T>? = null
    private var activeInput: I? = null
    private var previousSample: Sample<T>? = null

    init {
        require(this.episodeSignals.isNotEmpty()) { "Expectation episodes require at least one signal" }
        require(this.episodeSignals.distinct().size == this.episodeSignals.size) {
            "Expectation episode signals must be unique"
        }
        require(targetSignal in this.episodeSignals) {
            "Expectation episode signals must include the target signal"
        }
        require(!episodeContextBefore.isNegative) { "Episode context cannot be negative" }
    }

    val expectations: List<Expectation<T>>
        get() = resolvedExpectations.toList() + listOfNotNull(activeExpectation)

    val experiences: List<ExpectationExperience<I, T>>
        get() = resolvedExperiences.toList()

    val active: Expectation<T>?
        get() = activeExpectation

    override fun onSenseCompleted(samples: List<Sample<*>>) {
        targetSamples(samples).forEach(::observe)
    }

    private fun observe(current: Sample<T>) {
        val input = inputAt(sampleStore, current.timestamp)
        if (input == null) {
            previousSample = current
            return
        }

        val expectation = activeExpectation
        if (expectation != null) {
            val assessment = policy.assess(expectation, previousSample, current)
            if (assessment != null) {
                val resolved = expectation.copy(result = assessment.result)
                val formationInput = checkNotNull(activeInput) {
                    "Active expectation must retain the input that formed it"
                }
                val replayCandidates = resolvedExperiences.toList()
                val experience = ExpectationExperience(
                    episode = EpisodeDefinition(
                        from = subtractContext(resolved.formedAt),
                        to = inclusiveEnd(assessment.result.timestamp),
                        signals = episodeSignals,
                    ),
                    expectation = resolved,
                    input = formationInput,
                    observedValue = assessment.observedValue,
                    priority = assessment.priority,
                )

                resolvedExpectations += resolved
                resolvedExperiences += experience

                model.train(formationInput, assessment.observedValue)
                replaySelector.select(replayCandidates)?.let { replay ->
                    model.train(replay.input, replay.observedValue)
                }

                activeExpectation = null
                activeInput = null
            }
        }

        if (activeExpectation == null) {
            val prediction = model.predict(input)
            require(prediction.signal == targetSignal) {
                "Prediction model returned ${prediction.signal.id}, expected ${targetSignal.id}"
            }
            activeExpectation = policy.open(prediction, current)
            activeInput = input
        }

        previousSample = current
    }

    @Suppress("UNCHECKED_CAST")
    private fun targetSamples(samples: List<Sample<*>>): List<Sample<T>> =
        samples
            .filter { it.signal == targetSignal }
            .sortedBy { it.timestamp }
            .map { it as Sample<T> }

    private fun subtractContext(formedAt: Instant): Instant =
        try {
            formedAt.minus(episodeContextBefore)
        } catch (_: DateTimeException) {
            Instant.MIN
        }

    private fun inclusiveEnd(timestamp: Instant): Instant =
        if (timestamp == Instant.MAX) Instant.MAX else timestamp.plusNanos(1)
}
