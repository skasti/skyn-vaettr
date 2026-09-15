package no.skasti.skynvaettr.training

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import no.skasti.skynvaettr.Vaettr
import no.skasti.skynvaettr.models.LearnedAttentionTransitionModel
import no.skasti.skynvaettr.models.TransitionPredictionDecoder
import no.skasti.skynvaettr.representation.SignalIdentityEmbedder
import no.skasti.skynvaettr.runtime.SensingProcessingGraph
import no.skasti.skynvaettr.signals.InMemorySampleStore
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.Signal

class TransitionPredictionTrainerTest {
    @Test
    fun `source transition forms prediction that resolves on later target transition`() {
        val dimmer = Signal<Double>("state.kitchen.dimmer")
        val light = Signal<Double>("state.kitchen.light")
        val sampleStore = InMemorySampleStore()
        val embedder = SignalIdentityEmbedder()
        val decoder = TransitionPredictionDecoder(embedder)
        val model = LearnedAttentionTransitionModel(
            signalEmbeddingDimensions = embedder.dimensions,
            seed = 1,
        )
        val graph = SensingProcessingGraph(
            sampleStore = sampleStore,
            models = listOf(model),
        )
        val trainer = TransitionPredictionTrainer(decoder)
        val vaettr = Vaettr(sampleStore, graph, listOf(trainer))

        vaettr.sense(
            listOf(
                Sample(dimmer, 0.0, Instant.parse("2026-01-01T00:00:00Z")),
                Sample(light, 0.0, Instant.parse("2026-01-01T00:00:00Z")),
            ),
        )
        vaettr.sense(
            listOf(
                Sample(dimmer, 0.8, Instant.parse("2026-01-01T00:05:00Z")),
                Sample(light, 0.0, Instant.parse("2026-01-01T00:05:00Z")),
            ),
        )

        assertEquals(0, trainer.records.size)
        assertEquals(0L, model.trainingExampleCount)

        vaettr.sense(
            listOf(
                Sample(dimmer, 0.8, Instant.parse("2026-01-01T00:15:00Z")),
                Sample(light, 0.8, Instant.parse("2026-01-01T00:15:00Z")),
            ),
        )

        assertEquals(1, trainer.records.size)
        assertEquals(dimmer.id, trainer.records.single().sourceTransition)
        assertEquals(light.id, trainer.records.single().actualSignal)
        assertEquals(0.8, trainer.records.single().actualValue, absoluteTolerance = 1e-9)
        assertEquals(1L, model.trainingExampleCount)
    }
}
