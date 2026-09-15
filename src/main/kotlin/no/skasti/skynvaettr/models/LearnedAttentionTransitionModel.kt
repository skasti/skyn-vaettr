package no.skasti.skynvaettr.models

import kotlin.math.exp
import kotlin.math.ln1p
import kotlin.math.sqrt
import kotlin.random.Random
import no.skasti.skynvaettr.attention.ScaledDotProductAttention
import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation

/**
 * Small dependency-free single-head attention model for next-transition predictions.
 *
 * Input position 0 is the source transition that triggered the prediction. Remaining positions are
 * ordinary sensory history. Q is learned from the source-transition position, while K and V are
 * learned from history positions. The attended context is decoded into latent signal identity and
 * numeric value; [TransitionPredictionDecoder] maps the latent identity to a known signal.
 *
 * This is deliberately an inspectable Q/K/V baseline rather than a full Transformer.
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
        (inputDimensions == null || input.dimensions == inputDimensions) && input.positions >= 2

    override fun forward(input: Representation): Representation {
        ensureInitialized(input)
        val state = forwardState(input)
        latestWeights = state.weights.clone()
        val experienceConfidence = (1.0 - exp(-trainingExampleCount / 32.0)).coerceIn(0.0, 1.0)
        return Representation.of(
            Embedding.from(state.signalOutput + doubleArrayOf(state.valueOutput, experienceConfidence)),
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

        val signalLoss = signalGradient.indices.sumOf { dimension ->
            val error = state.signalOutput[dimension] - targetEmbedding[dimension]
            error * error / signalEmbeddingDimensions.toDouble()
        }
        val valueError = state.valueOutput - targetEmbedding[signalEmbeddingDimensions]
        val loss = signalLoss + valueError * valueError
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
                keyGradient[position][dimension] = scoreGradient[position] * state.query[dimension] * scale
                projectedValueGradient[position][dimension] = state.weights[position] * contextGradient[dimension]
            }
        }

        val queryProjectionGradient = Array(input.dimensions) { DoubleArray(attentionDimensions) }
        for (inputDimension in state.queryInput.indices) {
            for (dimension in 0 until attentionDimensions) {
                queryProjectionGradient[inputDimension][dimension] =
                    state.queryInput[inputDimension] * queryGradient[dimension]
            }
        }

        val keyProjectionGradient = Array(input.dimensions) { DoubleArray(attentionDimensions) }
        val valueProjectionGradient = Array(input.dimensions) { DoubleArray(attentionDimensions) }
        for (position in state.historyInputs.indices) {
            for (inputDimension in state.historyInputs[position].indices) {
                for (dimension in 0 until attentionDimensions) {
                    keyProjectionGradient[inputDimension][dimension] +=
                        state.historyInputs[position][inputDimension] * keyGradient[position][dimension]
                    valueProjectionGradient[inputDimension][dimension] +=
                        state.historyInputs[position][inputDimension] * projectedValueGradient[position][dimension]
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
        require(input.positions >= 2) { "transition attention input needs a query position plus sensory history" }
        if (inputDimensions != null) {
            require(supports(input)) {
                "Expected input width $inputDimensions with at least two positions, got ${input.dimensions} x ${input.positions}"
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
        val normalized = input.map(::normalizedInput)
        val queryInput = normalized.first()
        val historyInputs = normalized.drop(1)
        val query = project(queryInput, queryProjection)
        val keys = historyInputs.map { project(it, keyProjection) }
        val values = historyInputs.map { project(it, valueProjection) }
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
            queryInput = queryInput,
            historyInputs = historyInputs,
            query = query,
            keys = keys,
            values = values,
            weights = result.weights.single(),
            context = context,
            signalOutput = signalOutput,
            valueOutput = valueOutput,
        )
    }

    /**
     * Sensory/event positions use signal identity followed by raw numeric value and relative time.
     * Signed-log scaling keeps large continuous values from dominating early gradients.
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

    private fun dot(left: DoubleArray, right: DoubleArray): Double =
        left.indices.sumOf { left[it] * right[it] }

    private fun randomWeight(): Double = random.nextDouble(-0.08, 0.08)

    private fun clip(value: Double): Double = value.coerceIn(-5.0, 5.0)

    private data class ForwardState(
        val queryInput: DoubleArray,
        val historyInputs: List<DoubleArray>,
        val query: DoubleArray,
        val keys: List<DoubleArray>,
        val values: List<DoubleArray>,
        val weights: DoubleArray,
        val context: DoubleArray,
        val signalOutput: DoubleArray,
        val valueOutput: Double,
    )
}
