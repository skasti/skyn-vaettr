package no.skasti.skynvaettr.training

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import no.skasti.skynvaettr.Vaettr
import no.skasti.skynvaettr.expectations.Expectation
import no.skasti.skynvaettr.expectations.ExpectationAssessment
import no.skasti.skynvaettr.expectations.ExpectationPolicy
import no.skasti.skynvaettr.expectations.ExpectationResult
import no.skasti.skynvaettr.expectations.Prediction
import no.skasti.skynvaettr.models.TrainablePredictionModel
import no.skasti.skynvaettr.signals.InMemorySampleStore
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.SampleStore
import no.skasti.skynvaettr.signals.Signal

class ExpectationTrainerTest {
    @Test
    fun `trainer is generic over model input and signal value`() {
        val signal = Signal<Int>("state.counter")
        val store = InMemorySampleStore()
        val model = RecordingIntModel(signal)
        val policy = object : ExpectationPolicy<Int> {
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
            targetSignal = signal,
            model = model,
            policy = policy,
            episodeSignals = listOf(signal),
            inputAt = { sampleStore: SampleStore, at: Instant ->
                sampleStore.latestAtOrBefore(signal.id, at)?.value?.toString()
            },
        )
        val vaettr = Vaettr(sampleStore = store, trainers = listOf(trainer))
        val t0 = Instant.parse("2026-01-01T00:00:00Z")
        val t1 = Instant.parse("2026-01-01T00:01:00Z")

        vaettr.sense(Sample(signal, 0, t0))
        vaettr.sense(Sample(signal, 1, t1))

        val experience = trainer.experiences.single()
        assertEquals("0", model.training.single().first)
        assertEquals(1, model.training.single().second)
        assertEquals(t0, experience.episode.from)
        assertEquals(t1.plusNanos(1), experience.episode.to)
        assertEquals(2.0, experience.priority)
        assertEquals("Reached", assertNotNull(experience.expectation.result).value)
    }

    private class RecordingIntModel(
        private val signal: Signal<Int>,
    ) : TrainablePredictionModel<String, Int> {
        val training = mutableListOf<Pair<String, Int>>()

        override fun predict(input: String): Prediction<Int> =
            Prediction(
                signal = signal,
                value = input.toInt() + 1,
                confidence = 0.5,
            )

        override fun train(
            input: String,
            target: Int,
            weight: Double,
        ) {
            training += input to target
        }
    }
}
