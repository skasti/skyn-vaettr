package no.skasti.skynvaettr.training

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import no.skasti.skynvaettr.Vaettr
import no.skasti.skynvaettr.expectations.Expectation
import no.skasti.skynvaettr.expectations.ExpectationAssessment
import no.skasti.skynvaettr.expectations.ExpectationPolicy
import no.skasti.skynvaettr.expectations.ExpectationResult
import no.skasti.skynvaettr.expectations.Prediction
import no.skasti.skynvaettr.models.Model
import no.skasti.skynvaettr.models.PredictionDecoder
import no.skasti.skynvaettr.models.TrainableModel
import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.runtime.PredictionExecution
import no.skasti.skynvaettr.runtime.ProcessingGraph
import no.skasti.skynvaettr.signals.InMemorySampleStore
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.Signal

class ExpectationTrainerTest {
    @Test
    fun `trainer discovers graph model and trains the representation used for inference`() {
        val signal = Signal<Int>("state.counter")
        val store = InMemorySampleStore()
        val model = RecordingIntModel()
        val decoder = IntPredictionDecoder(signal)
        val graph = RecordingGraph(model, decoder)
        val policy = object : ExpectationPolicy<Int> {
            override fun supports(prediction: Prediction<*>): Boolean = prediction.value is Int

            override fun open(
                prediction: Prediction<Int>,
                current: Sample<Int>,
            ): Expectation<Int> =
                Expectation(
                    signal = current.signal,
                    signalInitialValue = current.value,
                    value = current.value + 1,
                    formedAt = current.timestamp,
                    confidence = prediction.confidence,
                )

            override fun assess(
                expectation: Expectation<Int>,
                previous: Sample<Int>?,
                current: Sample<Int>,
            ): ExpectationAssessment<Int>? =
                if (current.value >= expectation.value) {
                    ExpectationAssessment(
                        result = ExpectationResult("Reached", current.timestamp),
                        observedValue = current.value,
                        priority = 2.0,
                    )
                } else {
                    null
                }
        }
        val trainer = ExpectationTrainer(
            sampleStore = store,
            policy = policy,
        )
        val vaettr = Vaettr(
            sampleStore = store,
            graph = graph,
            trainers = listOf(trainer),
        )
        val t0 = Instant.parse("2026-01-01T00:00:00Z")
        val t1 = Instant.parse("2026-01-01T00:01:00Z")

        vaettr.sense(Sample(signal, 0, t0))
        val formationInput = graph.predictionExecutions().single().input
        vaettr.sense(Sample(signal, 1, t1))

        val experience = trainer.experiences.single()
        assertSame(model, experience.model)
        assertEquals(formationInput.toList(), model.training.single().first.toList())
        assertEquals(1, model.training.single().second[0][0].toInt())
        assertEquals(t0, experience.episode.from)
        assertEquals(t1.plusNanos(1), experience.episode.to)
        assertEquals(listOf(signal), experience.episode.signals)
        assertEquals(2.0, experience.priority)
        assertEquals("Reached", assertNotNull(experience.expectation.result).value)
    }

    private class RecordingIntModel : TrainableModel {
        val training = mutableListOf<Pair<Representation, Representation>>()

        override fun forward(input: Representation): Representation =
            Representation.of(Embedding.of(input[0][0] + 1.0, 0.5))

        override fun train(
            input: Representation,
            target: Representation,
            weight: Double,
        ) {
            training += input to target
        }
    }

    private class IntPredictionDecoder(
        override val signal: Signal<Int>,
    ) : PredictionDecoder<Int> {
        override fun decode(output: Representation): Prediction<Int> =
            Prediction(
                signal = signal,
                value = output[0][0].toInt(),
                confidence = output[0][1],
            )

        override fun trainingTarget(value: Int): Representation =
            Representation.of(Embedding.of(value.toDouble()))
    }

    private class RecordingGraph(
        private val model: TrainableModel,
        private val decoder: PredictionDecoder<Int>,
    ) : ProcessingGraph {
        private var executions = emptyList<PredictionExecution>()

        override fun sense(samples: List<Sample<*>>) {
            val current = samples.single().value as Int
            val input = Representation.of(Embedding.of(current.toDouble()))
            val output = model.forward(input)
            executions = listOf(
                PredictionExecution(
                    model = model,
                    decoder = decoder,
                    input = input,
                    output = output,
                    prediction = decoder.decode(output),
                ),
            )
        }

        override fun models(): List<Model> = listOf(model)

        override fun predictionExecutions(): List<PredictionExecution> = executions
    }
}
