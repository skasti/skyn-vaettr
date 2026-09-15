package no.skasti.skynvaettr.training

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import no.skasti.skynvaettr.expectations.Prediction
import no.skasti.skynvaettr.models.NumericPredictionDecoder
import no.skasti.skynvaettr.models.TrainableModel
import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.runtime.PredictionExecution
import no.skasti.skynvaettr.runtime.ProcessingGraph
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.Signal

class ObservationTrainerTest {
    @Test
    fun `trains previous execution from later observed value without an expectation`() {
        val signal = Signal<Double>("state.light")
        val model = RecordingModel()
        val decoder = NumericPredictionDecoder(signal)
        val input = Representation.of(Embedding.of(1.0, 0.5))
        val graph = object : ProcessingGraph {
            override fun sense(samples: List<Sample<*>>) = Unit
            override fun predictionExecutions(): List<PredictionExecution> = listOf(
                PredictionExecution(
                    model = model,
                    decoder = decoder,
                    input = input,
                    output = Representation.of(Embedding.of(0.2, 0.0)),
                    prediction = Prediction(signal, 0.2, 0.0),
                ),
            )
        }
        val trainer = ObservationTrainer()

        trainer.onSenseCompleted(
            listOf(Sample(signal, 0.2, Instant.parse("2026-01-01T00:00:00Z"))),
            graph,
        )
        assertEquals(emptyList(), model.targets)

        trainer.onSenseCompleted(
            listOf(Sample(signal, 0.8, Instant.parse("2026-01-01T00:05:00Z"))),
            graph,
        )

        assertEquals(listOf(0.8), model.targets)
        assertEquals(1, trainer.trainingExampleCount)
    }

    private class RecordingModel : TrainableModel {
        val targets = mutableListOf<Double>()

        override fun forward(input: Representation): Representation =
            Representation.of(Embedding.of(0.0, 0.0))

        override fun train(input: Representation, target: Representation, weight: Double) {
            targets += target[0][0]
        }
    }
}
