package no.skasti.skynvaettr.runtime

import java.time.Instant
import kotlin.math.abs
import kotlin.math.max
import no.skasti.skynvaettr.expectations.Prediction
import no.skasti.skynvaettr.models.LearnedAttentionTransitionModel
import no.skasti.skynvaettr.models.Model
import no.skasti.skynvaettr.models.TransitionPredictionDecoder
import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.SampleStore
import no.skasti.skynvaettr.signals.Signal
import no.skasti.skynvaettr.signals.SignalId

/** One meaningful numeric transition used to trigger the next-transition prediction path. */
data class NumericTransitionEvent(
    val previous: Sample<Double>,
    val current: Sample<Double>,
)

/** Runtime transition detector. Detection belongs to inference triggering, not to training. */
class NumericTransitionEventDetector(
    private val rangeFraction: Double = 0.05,
    private val rangeFloor: Double = 1.0,
) {
    private data class State(
        var minimum: Double,
        var maximum: Double,
        var lastSample: Sample<Double>,
        var anchor: Sample<Double>,
    )

    private val states = mutableMapOf<SignalId, State>()

    init {
        require(rangeFraction > 0.0)
        require(rangeFloor > 0.0 && rangeFloor.isFinite())
    }

    fun observe(sample: Sample<Double>): NumericTransitionEvent? {
        require(sample.value.isFinite())
        val state = states[sample.signal.id]
        if (state == null) {
            states[sample.signal.id] = State(sample.value, sample.value, sample, sample)
            return null
        }

        state.minimum = minOf(state.minimum, sample.value)
        state.maximum = maxOf(state.maximum, sample.value)
        val scale = max(state.maximum - state.minimum, rangeFloor)
        val meaningful = abs(sample.value - state.anchor.value) >= scale * rangeFraction
        val previous = state.lastSample
        state.lastSample = sample
        if (!meaningful) return null

        state.anchor = sample
        return NumericTransitionEvent(previous, sample)
    }
}

/** One complete event-triggered inference performed by the processing graph. */
data class TransitionPredictionExecution(
    val model: LearnedAttentionTransitionModel,
    val decoder: TransitionPredictionDecoder,
    val input: Representation,
    val prediction: Prediction<Double>,
    val sourceTransition: NumericTransitionEvent,
    val historyPositions: List<SensoryPosition>,
    val attentionWeights: List<DoubleArray>,
)

/** Exposes transition predictions to supervision without giving trainers ownership of inference. */
interface TransitionPredictionSource {
    fun latestTransitionPredictionExecution(): TransitionPredictionExecution?
}

/**
 * Explicit processing pipeline for horizon-free next-transition prediction.
 *
 * Samples -> sensory history -> tokenization/encoding -> Representation -> self-attention Q/K/V
 * -> contextualized sensory positions -> transition head -> decoder -> Prediction.
 *
 * Meaningful transition detection only decides when inference runs. It does not manufacture a query
 * token or otherwise tell the model which historical relationship to use.
 */
class TransitionPredictionProcessingGraph(
    sampleStore: SampleStore,
    private val model: LearnedAttentionTransitionModel,
    private val decoder: TransitionPredictionDecoder,
    private val sensoryEncoder: SensoryRepresentationEncoder = SensoryRepresentationEncoder(sampleStore),
    private val transitionDetector: NumericTransitionEventDetector = NumericTransitionEventDetector(),
) : ProcessingGraph, TransitionPredictionSource {
    var latestRepresentation: Representation? = null
        private set

    var latestPositions: List<SensoryPosition> = emptyList()
        private set

    private var latestExecution: TransitionPredictionExecution? = null

    override fun sense(samples: List<Sample<*>>) {
        if (samples.isEmpty()) return

        val now = samples.maxOf { it.timestamp }
        val frame = encodeHistory(now) ?: return clearFrame()
        latestRepresentation = frame.representation
        latestPositions = frame.positions

        observeKnownSignals(samples)
        val sourceTransition = detectSourceTransition(samples)
        if (sourceTransition == null) {
            latestExecution = null
            return
        }

        latestExecution = predict(sourceTransition, frame)
    }

    override fun models(): List<Model> = listOf(model)

    override fun latestTransitionPredictionExecution(): TransitionPredictionExecution? = latestExecution

    private fun encodeHistory(now: Instant): SensoryFrame? = sensoryEncoder.frame(now)

    private fun observeKnownSignals(samples: List<Sample<*>>) {
        samples.forEach { sample ->
            val value = sample.value as? Double ?: return@forEach
            @Suppress("UNCHECKED_CAST")
            val signal = sample.signal as Signal<Double>
            require(value.isFinite())
            decoder.observe(signal)
        }
    }

    private fun detectSourceTransition(samples: List<Sample<*>>): NumericTransitionEvent? =
        samples
            .mapNotNull { sample ->
                val value = sample.value as? Double ?: return@mapNotNull null
                @Suppress("UNCHECKED_CAST")
                val signal = sample.signal as Signal<Double>
                transitionDetector.observe(Sample(signal, value, sample.timestamp))
            }
            .sortedWith(compareBy<NumericTransitionEvent>({ it.current.timestamp }, { it.current.signal.id.value }))
            .firstOrNull()

    private fun predict(
        sourceTransition: NumericTransitionEvent,
        frame: SensoryFrame,
    ): TransitionPredictionExecution {
        val modelInput = frame.representation
        val latentOutput = model.forward(modelInput)
        val prediction = decoder.decode(latentOutput)

        return TransitionPredictionExecution(
            model = model,
            decoder = decoder,
            input = modelInput,
            prediction = prediction,
            sourceTransition = sourceTransition,
            historyPositions = frame.positions,
            attentionWeights = model.latestAttentionWeights(),
        )
    }

    private fun clearFrame() {
        latestRepresentation = null
        latestPositions = emptyList()
        latestExecution = null
    }
}
