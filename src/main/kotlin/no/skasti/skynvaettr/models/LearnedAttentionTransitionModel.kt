package no.skasti.skynvaettr.models

import kotlin.math.exp
import kotlin.math.ln1p
import kotlin.math.sqrt
import kotlin.random.Random
import no.skasti.skynvaettr.attention.ScaledDotProductAttention
import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation

/**
 * Small dependency-free single-query attention model used to learn next-transition predictions.
 *
 * It learns Q, K and V projections end-to-end from transition targets. The query is a learned
 * "what happens next?" vector; keys and values are projected from every sensory position. The
 * attended context is decoded into a latent signal identity plus numeric value. A separate
 * [TransitionPredictionDecoder] maps that latent identity back to one observed signal.
 *
 * This is intentionally a minimal inspectable baseline rather than a full Transformer. In
 * particular it uses one attention head and one learned global query so CI reports can expose the
 * exact attention weights that contributed to each prediction.
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
    private lateinit var keyProjection: Array<DoubleArray>
    private lateinit var valueProjection: Array<DoubleArray>
    private lateinit var signalProjection: Array<DoubleArray>
    private lateinit var valueHead: DoubleArray
    private lateinit var signalBias: DoubleArray
    private var valueBias: Double = 0.0
    private val query = DoubleArray(attentionDimensions) { randomWeight() }

    private var latestWeights: DoubleArray = doubleArrayOf()

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
        inputDimensions == null || input.dimensions == inputDimensions

    override fun forward(input: Representation): Representation {
        ensureInitialized(input)
        val state = forwardState(input)
        latestWeights = state.weights.clone()
        val experienceConfidence = (1.0 - exp(-trainingExampleCount / 32.0)).coerceIn(0.0, 1.0)
        return Representation.of(
            Embedding.from(
                state.signalOutput + doubleArrayOf(state.valueOutput, experienceConfidence),
            ),
        )
    }

    fun latestAttentionWeights(): DoubleArray = latestWeights.clone()

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

        val loss = signalGradient.indices.sumOf { dimension ->
            val error = state.signalOutput[dimension] - targetEmbedding[dimension]
            error * error / signalEmbeddingDimensions.toDouble()
        } + run {
            val error = state.valueOutput - targetEmbedding[signalEmbeddingDimensions]
            error * error
        }
        exponentialMovingLoss = exponentialMovingLoss?.let { previous -> previous * 0.95 + loss * 0.05 } ?: loss

        val contextGradient = DoubleArray(attentionDimensions)
        for (contextDimension in 0 until attentionDimensions) {
            for (signalDimension in 0 until signalEmbeddingDimensions) {
                contextGradient[contextDimension] +=
                    signalGradient[signalDimension] * signalProjection[contextDimension][signalDimension]
            }
            contextGradient[contextDimension] += valueGradient * valueHead[contextDimension]
        }

        val signalProjectionGradient = Array(attentionDimensions) { DoubleArray(signalEmbeddingDimensions) }
        for (contextDimension in 0 until attentionDimensions) {
            for (signalDimension in 0 until signalEmbeddingDimensions) {
                signalProjectionGradient[contextDimension][signalDimension] =
                    state.context[contextDimension] * signalGradient[signalDimension]
            }
        }
        val valueHeadGradient = DoubleArray(attentionDimensions) { dimension ->
            state.context[dimension] * valueGradient
        }

        val attentionGradient = DoubleArray(state.weights.size) { position ->
            dot(contextGradient, state.values[position])
        }
        val weightedAttentionGradient = state.weights.indices.sumOf { position ->
            state.weights[position] * attentionGradient[position]
        }
        val scoreGradient = DoubleArray(state.weights.size) { position ->
            state.weights[position] * (attentionGradient[position] - weightedAttentionGradient)
        }

        val scale = 1.0 / sqrt(attentionDimensions.toDouble())
        val queryGradient = DoubleArray(attentionDimensions)
        val keyGradient = Array(state.weights.size) { DoubleArray(attentionDimensions) }
        val projectedValueGradient = Array(state.weights.size) { DoubleArray(attentionDimensions) }
        for (position in state.weights.indices) {
            for (dimension in 0 until attentionDimensions) {
                queryGradient[dimension] += scoreGradient[position] * state.keys[position][dimension] * scale
                keyGradient[position][dimension] = scoreGradient[position] * query[dimension] * scale
                projectedValueGradient[position][dimension] = state.weights[position] * contextGradient[dimension]
            }
        }

        val keyProjectionGradient = Array(input.dimensions) { DoubleArray(attentionDimensions) }
        val valueProjectionGradient = Array(input.dimensions) { DoubleArray(attentionDimensions) }
        for (position in state.inputs.indices) {
            for (inputDimension in state.inputs[position].indices) {
                for (dimension in 0 until attentionDimensions) {
                    keyProjectionGradient[inputDimension][dimension] +=
                        state.inputs[position][inputDimension] * keyGradient[position][dimension]
                    valueProjectionGradient[inputDimension][dimension] +=
                        state.inputs[position][inputDimension] * projectedValueGradient[position][dimension]
                }
            }
        }

        val step = learningRate * weight
        for (dimension in query.indices) {
            query[dimension] -= step * clip(queryGradient[dimension])
        }
        for (inputDimension in keyProjection.indices) {
            for (dimension in 0 until attentionDimensions) {
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
        if (inputDimensions != null) {
            require(supports(input)) {
                "Expected input width $inputDimensions, got ${input.dimensions}"
            }
            return
        }
        inputDimensions = input.dimensions
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
        val keys = inputs.map { project(it, keyProjection) }
        val values = inputs.map { project(it, valueProjection) }
        val result = attention.apply(
            queries = Representation.of(Embedding.from(query)),
            keys = Representation.from(keys.map(Embedding::from)),
            values = Representation.from(values.map(Embedding::from)),
        )
        val context = result.output[0].toDoubleArray()
        val signalOutput = DoubleArray(signalEmbeddingDimensions) { signalDimension ->
            signalBias[signalDimension] +
                context.indices.sumOf { dimension ->
                    context[dimension] * signalProjection[dimension][signalDimension]
                }
        }
        val valueOutput = valueBias + context.indices.sumOf { dimension -> context[dimension] * valueHead[dimension] }
        return ForwardState(
            inputs = inputs,
            keys = keys,
            values = values,
            weights = result.weights.single(),
            context = context,
            signalOutput = signalOutput,
            valueOutput = valueOutput,
        )
    }

    /**
     * The sensing graph currently appends raw numeric value and relative time after signal identity.
     * Signed-log scaling keeps large continuous values from dominating early gradients while
     * preserving direction and fine structure around zero.
     */
    private fun normalizedInput(embedding: Embedding): DoubleArray =
        embedding.toDoubleArray().also { values ->
            if (values.size >= 2) {
                val valueIndex = values.lastIndex - 1
                val value = values[valueIndex]
                values[valueIndex] = if (value == 0.0) 0.0 else kotlin.math.sign(value) * ln1p(kotlin.math.abs(value))
            }
        }

    private fun project(
        input: DoubleArray,
        matrix: Array<DoubleArray>,
    ): DoubleArray =
        DoubleArray(attentionDimensions) { outputDimension ->
            input.indices.sumOf { inputDimension ->
                input[inputDimension] * matrix[inputDimension][outputDimension]
            }
        }

    private fun dot(
        left: DoubleArray,
        right: DoubleArray,
    ): Double = left.indices.sumOf { left[it] * right[it] }

    private fun randomWeight(): Double = random.nextDouble(-0.08, 0.08)

    private fun clip(value: Double): Double = value.coerceIn(-5.0, 5.0)

    private data class ForwardState(
        val inputs: List<DoubleArray>,
        val keys: List<DoubleArray>,
        val values: List<DoubleArray>,
        val weights: DoubleArray,
        val context: DoubleArray,
        val signalOutput: DoubleArray,
        val valueOutput: Double,
    )
}
