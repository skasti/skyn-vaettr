package no.skasti.skynvaettr.training

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import no.skasti.skynvaettr.expectations.Prediction
import no.skasti.skynvaettr.models.NumericPredictionDecoder
import no.skasti.skynvaettr.models.TrainableModel
import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.runtime.PredictionExecution
import no.skasti.skynvaettr.runtime.ProcessingGraph
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.Signal

class TransitionTrainerTest {
    @Test
    fun `trains light from context captured when dimmer transitioned`() {
        val dimmer = Signal<Double>("state.kitchen.dimmer")
        val light = Signal<Double>("state.kitchen.light")
        val model = RecordingModel()
        val decoder = NumericPredictionDecoder(light)
        var input = Representation.of(Embedding.of(1.0, 0.0))
        val graph = object : ProcessingGraph {
            override fun sense(samples: List<Sample<*>>) = Unit
            override fun predictionExecutions(): List<PredictionExecution> = listOf(
                PredictionExecution(
                    model = model,
                    decoder = decoder,
                    input = input,
                    output = Representation.of(Embedding.of(0.2, 0.5)),
                    prediction = Prediction(light, 0.2, 0.5),
                ),
            )
        }
        val trainer = TransitionTrainer(
            objectiveWeights = AdaptiveObjectiveWeights(smoothing = 1.0),
            detector = NumericTransitionDetector(rangeFraction = 0.05, rangeFloor = 1.0),
        )

        trainer.onSenseCompleted(
            listOf(
                Sample(dimmer, 0.2, Instant.EPOCH),
                Sample(light, 0.2, Instant.EPOCH),
            ),
            graph,
        )

        input = Representation.of(Embedding.of(2.0, 0.0))
        trainer.onSenseCompleted(
            listOf(
                Sample(dimmer, 0.8, Instant.EPOCH.plusSeconds(300)),
                Sample(light, 0.2, Instant.EPOCH.plusSeconds(300)),
            ),
            graph,
        )

        input = Representation.of(Embedding.of(3.0, 0.0))
        trainer.onSenseCompleted(
            listOf(
                Sample(dimmer, 0.8, Instant.EPOCH.plusSeconds(600)),
                Sample(light, 0.8, Instant.EPOCH.plusSeconds(600)),
            ),
            graph,
        )

        assertEquals(listOf(0.8), model.targets)
        assertEquals(2.0, model.inputs.single()[0][0])
        assertTrue(model.weights.single() > 0.0)
        assertEquals(2, trainer.detectedTransitionCount)
        assertEquals(1, trainer.trainingExampleCount)
    }

    private class RecordingModel : TrainableModel {
        val inputs = mutableListOf<Representation>()
        val targets = mutableListOf<Double>()
        val weights = mutableListOf<Double>()

        override fun forward(input: Representation): Representation =
            Representation.of(Embedding.of(0.0, 0.0))

        override fun train(input: Representation, target: Representation, weight: Double) {
            inputs += input
            targets += target[0][0]
            weights += weight
        }
    }
}
