package no.skasti.skynvaettr.runtime

import java.time.Duration
import no.skasti.skynvaettr.models.Model
import no.skasti.skynvaettr.models.PredictionDecoder
import no.skasti.skynvaettr.models.PredictionRoute
import no.skasti.skynvaettr.models.PredictionRouteFactory
import no.skasti.skynvaettr.representation.Embedder
import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.representation.SignalIdentityEmbedder
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.SampleStore
import no.skasti.skynvaettr.signals.SignalId

/**
 * General sensory graph retained for ordinary prediction routes.
 *
 * Preprocessing is delegated to [SensoryRepresentationEncoder], making the active stages explicit:
 * history selection -> signal-id tokenization/encoding -> sensory [Representation]. This graph then
 * runs any configured ordinary prediction routes over that representation.
 *
 * Event-conditioned QKV prediction uses [TransitionPredictionProcessingGraph] instead of hiding
 * transition inference inside a trainer.
 */
class SensingProcessingGraph(
    sampleStore: SampleStore,
    signalEmbedder: Embedder<SignalId> = SignalIdentityEmbedder(),
    historyAges: List<Duration> = DEFAULT_HISTORY_AGES,
    models: List<Model> = emptyList(),
    private val predictionDecoders: List<PredictionDecoder<*>> = emptyList(),
    private val predictionRouteFactories: List<PredictionRouteFactory> = emptyList(),
) : ProcessingGraph {
    private val sensoryEncoder = SensoryRepresentationEncoder(sampleStore, signalEmbedder, historyAges)
    private val modelComponents = models.toList()
    private val discoveredRoutes = linkedMapOf<SignalId, PredictionRoute>()

    var latestRepresentation: Representation? = null
        private set

    var latestPositions: List<SensoryPosition> = emptyList()
        private set

    private var latestExecutions: List<PredictionExecution> = emptyList()

    override fun sense(samples: List<Sample<*>>) {
        if (samples.isEmpty()) return

        discoverPredictionRoutes(samples)

        val now = samples.maxOf { it.timestamp }
        val frame = sensoryEncoder.frame(now)
        if (frame == null) {
            latestRepresentation = null
            latestPositions = emptyList()
            latestExecutions = emptyList()
            return
        }

        val representation = frame.representation
        latestRepresentation = representation
        latestPositions = frame.positions
        latestExecutions = buildList {
            if (predictionDecoders.isNotEmpty()) {
                modelComponents
                    .filter { it.supports(representation) }
                    .forEach { model ->
                        val output = model.forward(representation)
                        predictionDecoders
                            .filter { it.supports(output) }
                            .forEach { decoder ->
                                add(
                                    PredictionExecution(
                                        model = model,
                                        decoder = decoder,
                                        input = representation,
                                        output = output,
                                        prediction = decoder.decode(output),
                                    ),
                                )
                            }
                    }
            }

            discoveredRoutes.values.forEach { route ->
                if (!route.model.supports(representation)) return@forEach
                val output = route.model.forward(representation)
                if (!route.decoder.supports(output)) return@forEach
                add(
                    PredictionExecution(
                        model = route.model,
                        decoder = route.decoder,
                        input = representation,
                        output = output,
                        prediction = route.decoder.decode(output),
                    ),
                )
            }
        }
    }

    override fun models(): List<Model> =
        (modelComponents + discoveredRoutes.values.map { it.model }).distinctBy { System.identityHashCode(it) }

    override fun predictionExecutions(): List<PredictionExecution> = latestExecutions.toList()

    private fun discoverPredictionRoutes(samples: List<Sample<*>>) {
        samples.forEach { sample ->
            if (sample.signal.id in discoveredRoutes) return@forEach
            val route = predictionRouteFactories.firstNotNullOfOrNull { it.create(sample) } ?: return@forEach
            discoveredRoutes[sample.signal.id] = route
        }
    }

    companion object {
        val DEFAULT_HISTORY_AGES: List<Duration> = SensoryRepresentationEncoder.DEFAULT_HISTORY_AGES
    }
}
