package no.skasti.skynvaettr.training

import java.time.DateTimeException
import java.time.Duration
import java.time.Instant
import no.skasti.skynvaettr.episodes.EpisodeDefinition
import no.skasti.skynvaettr.expectations.Expectation
import no.skasti.skynvaettr.expectations.ExpectationPolicy
import no.skasti.skynvaettr.expectations.Prediction
import no.skasti.skynvaettr.models.PredictionDecoder
import no.skasti.skynvaettr.models.TrainableModel
import no.skasti.skynvaettr.runtime.PredictionExecution
import no.skasti.skynvaettr.runtime.ProcessingGraph
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.SampleStore

/**
 * Generic online trainer that turns resolved expectations into replayable episodes.
 *
 * Models are not configured on the trainer. The trainer discovers prediction-producing trainable
 * model executions from the completed [ProcessingGraph], retains the exact latent [Representation]
 * input used for inference, and trains the same model through the decoder's output-space target
 * mapping when an expectation resolves.
 */
class ExpectationTrainer<T>(
    private val sampleStore: SampleStore,
    private val policy: ExpectationPolicy<T>,
    private val replaySelector: ReplaySelector<ExpectationExperience<T>> = ReplaySelector { null },
    private val episodeContextBefore: Duration = Duration.ZERO,
) : Trainer {
    private class Active<T>(
        val model: TrainableModel,
        val decoder: PredictionDecoder<T>,
        val expectation: Expectation<T>,
        val input: no.skasti.skynvaettr.representation.Representation,
        var previousSample: Sample<T>,
    )

    private val resolvedExpectations = mutableListOf<Expectation<T>>()
    private val resolvedExperiences = mutableListOf<ExpectationExperience<T>>()
    private val activeExpectations = mutableListOf<Active<T>>()

    init {
        require(!episodeContextBefore.isNegative) { "Episode context cannot be negative" }
    }

    val expectations: List<Expectation<T>>
        get() = resolvedExpectations.toList() + activeExpectations.map { it.expectation }

    val experiences: List<ExpectationExperience<T>>
        get() = resolvedExperiences.toList()

    val active: List<Expectation<T>>
        get() = activeExpectations.map { it.expectation }

    override fun onSenseCompleted(
        samples: List<Sample<*>>,
        graph: ProcessingGraph,
    ) {
        resolveActive(samples)
        openFrom(graph.predictionExecutions(), samples)
    }

    private fun resolveActive(samples: List<Sample<*>>) {
        activeExpectations.toList().forEach { active ->
            val matchingSamples = samples
                .filter { it.signal == active.expectation.signal }
                .sortedBy { it.timestamp }

            for (sample in matchingSamples) {
                @Suppress("UNCHECKED_CAST")
                val current = sample as Sample<T>
                val assessment = policy.assess(active.expectation, active.previousSample, current)
                if (assessment == null) {
                    active.previousSample = current
                    continue
                }

                val resolved = active.expectation.copy(result = assessment.result)
                val from = subtractContext(resolved.formedAt)
                val to = inclusiveEnd(assessment.result.timestamp)
                val episodeSignals = sampleStore.get(from, to)
                    .map { it.signal }
                    .distinctBy { it.id }
                val replayCandidates = resolvedExperiences.toList()
                val experience = ExpectationExperience(
                    episode = EpisodeDefinition(
                        from = from,
                        to = to,
                        signals = episodeSignals,
                    ),
                    expectation = resolved,
                    model = active.model,
                    decoder = active.decoder,
                    input = active.input,
                    observedValue = assessment.observedValue,
                    priority = assessment.priority,
                )

                resolvedExpectations.add(resolved)
                resolvedExperiences.add(experience)
                active.model.train(
                    active.input,
                    active.decoder.trainingTarget(assessment.observedValue),
                )
                replaySelector.select(replayCandidates)?.let { replay ->
                    replay.model.train(
                        replay.input,
                        replay.decoder.trainingTarget(replay.observedValue),
                    )
                }
                activeExpectations.remove(active)
                break
            }
        }
    }

    private fun openFrom(
        executions: List<PredictionExecution>,
        samples: List<Sample<*>>,
    ) {
        executions.forEach { execution ->
            val model = execution.model as? TrainableModel ?: return@forEach
            if (!policy.supports(execution.prediction)) return@forEach

            @Suppress("UNCHECKED_CAST")
            val prediction = execution.prediction as Prediction<T>
            @Suppress("UNCHECKED_CAST")
            val decoder = execution.decoder as PredictionDecoder<T>

            if (activeExpectations.any {
                    it.model === model && it.expectation.signal == prediction.signal
                }
            ) {
                return@forEach
            }

            val current = samples
                .filter { it.signal == prediction.signal }
                .maxByOrNull { it.timestamp }
                ?: return@forEach
            @Suppress("UNCHECKED_CAST")
            val typedCurrent = current as Sample<T>

            val expectation = policy.open(prediction, typedCurrent) ?: return@forEach
            activeExpectations += Active(
                model = model,
                decoder = decoder,
                expectation = expectation,
                input = execution.input,
                previousSample = typedCurrent,
            )
        }
    }

    private fun subtractContext(formedAt: Instant): Instant =
        try {
            formedAt.minus(episodeContextBefore)
        } catch (_: DateTimeException) {
            Instant.MIN
        }

    private fun inclusiveEnd(timestamp: Instant): Instant =
        if (timestamp == Instant.MAX) Instant.MAX else timestamp.plusNanos(1)
}
