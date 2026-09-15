package no.skasti.skynvaettr.models

import kotlin.math.exp
import kotlin.math.ln1p
import kotlin.math.sqrt
import kotlin.random.Random
import no.skasti.skynvaettr.attention.ScaledDotProductAttention
import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation

/**
 * Small dependency-free single-head self-attention model for next-transition predictions.
 *
 * Every sensory position produces its own learned Q, K and V projection and may attend to every
 * other sensory position. The contextualized positions are mean-pooled into a shared world context
 * consumed by the transition head. Because every contextualized position contributes to the loss,
 * gradients train every attention row rather than only one event-specific query.
 *
 * This is deliberately an inspectable baseline rather than a full Transformer.
 */
class LearnedAttentionTransitionModel(
    private val signalEmbeddingDimensions: Int,
    private val attentionDimensions: Int = 8,
    private val learningRate: Double = 0.01,
    seed: Int = 37,
) : TrainableModel {
    private val attention = ScaledDotProductAttention()
    private val random = Random(seed)

    private var inputDimensions: Int? = null
    private lateinit var queryProjection: Array<DoubleArray>
    private lateinit var keyProjection: Array<DoubleArray>
    private lateinit var valueProjection: Array<DoubleArray>
    private lateinit var signalProjection: Array<DoubleArray>
    private lateinit var valueHead: DoubleArray
    private lateinit var signalBias: DoubleArray
    private var valueBias: Double = 0.0

    private var latestWeights: List<DoubleArray> = emptyList()

    var trainingExampleCount: Long = 0
        private set

    var exponentialMovingLoss: Double? = null
        private set

    init {
        require(signalEmbeddingDimensions > 0)
        require(attentionDimensions > 0)
        require(learningRate > 0.0 && learningRate.isFinite())
    }

    override fun supports(input: Representation): Boolean =
        input.positions > 0 && (inputDimensions == null || input.dimensions == inputDimensions)

    override fun forward(input: Representation): Representation {
        ensureInitialized(input)
        val state = forwardState(input)
        latestWeights = state.weights.map(DoubleArray::clone)
        val experienceConfidence = (1.0 - exp(-trainingExampleCount / 32.0)).coerceIn(0.0, 1.0)
        return Representation.of(
            Embedding.from(state.signalOutput + doubleArrayOf(state.valueOutput, experienceConfidence)),
        )
    }

    /** Row i is the attention distribution from input position i across all input positions. */
    fun latestAttentionWeights(): List<DoubleArray> = latestWeights.map(DoubleArray::clone)

    override fun train(
        input: Representation,
        target: Representation,
        weight: Double,
    ) {
        require(weight.isFinite() && weight >= 0.0)
        if (weight == 0.0) return
        ensureInitialized(input)
        require(target.positions == 1)
        require(target.dimensions == signalEmbeddingDimensions + 1) {
            "Expected ${signalEmbeddingDimensions + 1} transition target dimensions, got ${target.dimensions}"
        }

        val state = forwardState(input)
        val targetEmbedding = target[0]
        val signalGradient = DoubleArray(signalEmbeddingDimensions) { dimension ->
            2.0 * (state.signalOutput[dimension] - targetEmbedding[dimension]) /
                signalEmbeddingDimensions.toDouble()
        }
        val valueGradient = 2.0 * (state.valueOutput - targetEmbedding[signalEmbeddingDimensions])

        val signalLoss = signalGradient.indices.sumOf { dimension ->
            val error = state.signalOutput[dimension] - targetEmbedding[dimension]
            error * error / signalEmbeddingDimensions.toDouble()
        }
        val valueError = state.valueOutput - targetEmbedding[signalEmbeddingDimensions]
        val loss = signalLoss + valueError * valueError
        exponentialMovingLoss = exponentialMovingLoss?.let { previous -> previous * 0.95 + loss * 0.05 } ?: loss

        val pooledContextGradient = DoubleArray(attentionDimensions)
        for (contextDimension in 0 until attentionDimensions) {
            for (signalDimension in 0 until signalEmbeddingDimensions) {
                pooledContextGradient[contextDimension] +=
                    signalGradient[signalDimension] * signalProjection[contextDimension][signalDimension]
            }
            pooledContextGradient[contextDimension] += valueGradient * valueHead[contextDimension]
        }

        val signalProjectionGradient = Array(attentionDimensions) { DoubleArray(signalEmbeddingDimensions) }
        for (contextDimension in 0 until attentionDimensions) {
            for (signalDimension in 0 until signalEmbeddingDimensions) {
                signalProjectionGradient[contextDimension][signalDimension] =
                    state.pooledContext[contextDimension] * signalGradient[signalDimension]
            }
        }
        val valueHeadGradient = DoubleArray(attentionDimensions) { dimension ->
            state.pooledContext[dimension] * valueGradient
        }

        // Mean pooling gives every contextualized position an equal share of the output gradient.
        val contextGradient = DoubleArray(attentionDimensions) { dimension ->
            pooledContextGradient[dimension] / state.contexts.size.toDouble()
        }
        val scale = 1.0 / sqrt(attentionDimensions.toDouble())
        val queryGradients = Array(state.inputs.size) { DoubleArray(attentionDimensions) }
        val keyGradients = Array(state.inputs.size) { DoubleArray(attentionDimensions) }
        val valueGradients = Array(state.inputs.size) { DoubleArray(attentionDimensions) }

        state.weights.indices.forEach { queryIndex ->
            val attentionGradient = DoubleArray(state.inputs.size) { keyIndex ->
                dot(contextGradient, state.values[keyIndex])
            }
            val weightedAttentionGradient = state.weights[queryIndex].indices.sumOf { keyIndex ->
                state.weights[queryIndex][keyIndex] * attentionGradient[keyIndex]
            }
            val scoreGradient = DoubleArray(state.inputs.size) { keyIndex ->
                state.weights[queryIndex][keyIndex] *
                    (attentionGradient[keyIndex] - weightedAttentionGradient)
            }

            state.inputs.indices.forEach { keyIndex ->
                for (dimension in 0 until attentionDimensions) {
                    queryGradients[queryIndex][dimension] +=
                        scoreGradient[keyIndex] * state.keys[keyIndex][dimension] * scale
                    keyGradients[keyIndex][dimension] +=
                        scoreGradient[keyIndex] * state.queries[queryIndex][dimension] * scale
                    valueGradients[keyIndex][dimension] +=
                        state.weights[queryIndex][keyIndex] * contextGradient[dimension]
                }
            }
        }

        val queryProjectionGradient = Array(input.dimensions) { DoubleArray(attentionDimensions) }
        val keyProjectionGradient = Array(input.dimensions) { DoubleArray(attentionDimensions) }
        val valueProjectionGradient = Array(input.dimensions) { DoubleArray(attentionDimensions) }
        state.inputs.indices.forEach { position ->
            state.inputs[position].indices.forEach { inputDimension ->
                for (dimension in 0 until attentionDimensions) {
                    val inputValue = state.inputs[position][inputDimension]
                    queryProjectionGradient[inputDimension][dimension] +=
                        inputValue * queryGradients[position][dimension]
                    keyProjectionGradient[inputDimension][dimension] +=
                        inputValue * keyGradients[position][dimension]
                    valueProjectionGradient[inputDimension][dimension] +=
                        inputValue * valueGradients[position][dimension]
                }
            }
        }

        val step = learningRate * weight
        for (inputDimension in queryProjection.indices) {
            for (dimension in 0 until attentionDimensions) {
                queryProjection[inputDimension][dimension] -= step * clip(queryProjectionGradient[inputDimension][dimension])
                keyProjection[inputDimension][dimension] -= step * clip(keyProjectionGradient[inputDimension][dimension])
                valueProjection[inputDimension][dimension] -= step * clip(valueProjectionGradient[inputDimension][dimension])
            }
        }
        for (contextDimension in 0 until attentionDimensions) {
            for (signalDimension in 0 until signalEmbeddingDimensions) {
                signalProjection[contextDimension][signalDimension] -=
                    step * clip(signalProjectionGradient[contextDimension][signalDimension])
            }
            valueHead[contextDimension] -= step * clip(valueHeadGradient[contextDimension])
        }
        for (signalDimension in 0 until signalEmbeddingDimensions) {
            signalBias[signalDimension] -= step * clip(signalGradient[signalDimension])
        }
        valueBias -= step * clip(valueGradient)
        trainingExampleCount++
    }

    private fun ensureInitialized(input: Representation) {
        require(input.positions > 0) { "self-attention requires at least one sensory position" }
        if (inputDimensions != null) {
            require(supports(input)) {
                "Expected input width $inputDimensions, got ${input.dimensions}"
            }
            return
        }
        inputDimensions = input.dimensions
        queryProjection = Array(input.dimensions) { DoubleArray(attentionDimensions) { randomWeight() } }
        keyProjection = Array(input.dimensions) { DoubleArray(attentionDimensions) { randomWeight() } }
        valueProjection = Array(input.dimensions) { DoubleArray(attentionDimensions) { randomWeight() } }
        signalProjection = Array(attentionDimensions) {
            DoubleArray(signalEmbeddingDimensions) { randomWeight() }
        }
        valueHead = DoubleArray(attentionDimensions) { randomWeight() }
        signalBias = DoubleArray(signalEmbeddingDimensions)
    }

    private fun forwardState(input: Representation): ForwardState {
        val inputs = input.map(::normalizedInput)
        val queries = inputs.map { project(it, queryProjection) }
        val keys = inputs.map { project(it, keyProjection) }
        val values = inputs.map { project(it, valueProjection) }
        val result = attention.apply(
            queries = Representation.from(queries.map(Embedding::from)),
            keys = Representation.from(keys.map(Embedding::from)),
            values = Representation.from(values.map(Embedding::from)),
        )
        val contexts = result.output.map(Embedding::toDoubleArray)
        val pooledContext = DoubleArray(attentionDimensions) { dimension ->
            contexts.sumOf { it[dimension] } / contexts.size.toDouble()
        }
        val signalOutput = DoubleArray(signalEmbeddingDimensions) { signalDimension ->
            signalBias[signalDimension] +
                pooledContext.indices.sumOf { dimension ->
                    pooledContext[dimension] * signalProjection[dimension][signalDimension]
                }
        }
        val valueOutput = valueBias +
            pooledContext.indices.sumOf { dimension -> pooledContext[dimension] * valueHead[dimension] }
        return ForwardState(
            inputs = inputs,
            queries = queries,
            keys = keys,
            values = values,
            weights = result.weights,
            contexts = contexts,
            pooledContext = pooledContext,
            signalOutput = signalOutput,
            valueOutput = valueOutput,
        )
    }

    /** Sensory positions use signal identity followed by raw numeric value and relative time. */
    private fun normalizedInput(embedding: Embedding): DoubleArray =
        embedding.toDoubleArray().also { values ->
            if (values.size >= 2) {
                val valueIndex = values.lastIndex - 1
                val value = values[valueIndex]
                values[valueIndex] = if (value == 0.0) 0.0 else kotlin.math.sign(value) * ln1p(kotlin.math.abs(value))
            }
        }

    private fun project(input: DoubleArray, matrix: Array<DoubleArray>): DoubleArray =
        DoubleArray(attentionDimensions) { outputDimension ->
            input.indices.sumOf { inputDimension -> input[inputDimension] * matrix[inputDimension][outputDimension] }
        }

    private fun dot(left: DoubleArray, right: DoubleArray): Double =
        left.indices.sumOf { left[it] * right[it] }

    private fun randomWeight(): Double = random.nextDouble(-0.08, 0.08)

    private fun clip(value: Double): Double = value.coerceIn(-5.0, 5.0)

    private data class ForwardState(
        val inputs: List<DoubleArray>,
        val queries: List<DoubleArray>,
        val keys: List<DoubleArray>,
        val values: List<DoubleArray>,
        val weights: List<DoubleArray>,
        val contexts: List<DoubleArray>,
        val pooledContext: DoubleArray,
        val signalOutput: DoubleArray,
        val valueOutput: Double,
    )
}
