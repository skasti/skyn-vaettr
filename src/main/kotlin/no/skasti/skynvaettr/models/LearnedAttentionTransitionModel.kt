package no.skasti.skynvaettr.models

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.sqrt
import kotlin.random.Random
import no.skasti.skynvaettr.attention.ScaledDotProductAttention
import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation

/**
 * Small dependency-free candidate-conditioned attention model for next-transition predictions.
 *
 * Every distinct signal identity present in the sensory history becomes one candidate query. The
 * same learned query projection is therefore reused for every candidate signal, while K/V are
 * projected from the complete sensory history. Each candidate gets its own attention row, next-
 * transition logit and candidate-specific value prediction.
 *
 * Training uses cross-entropy over candidate logits for target-signal identity and a numeric value
 * loss only for the observed target candidate. This avoids regressing toward an average point
 * between signal identity embeddings.
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
    private lateinit var scoreContextHead: DoubleArray
    private lateinit var scoreQueryHead: DoubleArray
    private lateinit var valueContextHead: DoubleArray
    private lateinit var valueQueryHead: DoubleArray
    private var scoreBias: Double = 0.0
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
        input.positions > 0 &&
            input.dimensions > signalEmbeddingDimensions &&
            (inputDimensions == null || input.dimensions == inputDimensions)

    override fun forward(input: Representation): Representation {
        ensureInitialized(input)
        val state = forwardState(input)
        latestWeights = state.weights.map(DoubleArray::clone)
        val experienceConfidence = (1.0 - exp(-trainingExampleCount / 32.0)).coerceIn(0.0, 1.0)

        return Representation.from(
            state.candidateIdentities.indices.map { candidateIndex ->
                Embedding.from(
                    state.candidateIdentities[candidateIndex] +
                        doubleArrayOf(
                            state.logits[candidateIndex],
                            state.valueOutputs[candidateIndex],
                            experienceConfidence,
                        ),
                )
            },
        )
    }

    /** Row i is candidate i's attention distribution across sensory-history positions. */
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
        val targetIdentity = DoubleArray(signalEmbeddingDimensions) { targetEmbedding[it] }
        val targetIndex = state.candidateIdentities.indices.minBy { candidateIndex ->
            identityDistance(state.candidateIdentities[candidateIndex], targetIdentity)
        }
        val targetValue = targetEmbedding[signalEmbeddingDimensions]
        val probabilities = softmax(state.logits)

        val scoreGradients = DoubleArray(state.logits.size) { candidateIndex ->
            probabilities[candidateIndex] - if (candidateIndex == targetIndex) 1.0 else 0.0
        }
        val valueError = state.valueOutputs[targetIndex] - targetValue
        val valueGradient = 2.0 * valueError
        val loss = -ln(probabilities[targetIndex].coerceAtLeast(1e-12)) + valueError * valueError
        exponentialMovingLoss = exponentialMovingLoss?.let { previous -> previous * 0.95 + loss * 0.05 } ?: loss

        val scoreContextHeadGradient = DoubleArray(attentionDimensions)
        val scoreQueryHeadGradient = DoubleArray(attentionDimensions)
        val valueContextHeadGradient = DoubleArray(attentionDimensions)
        val valueQueryHeadGradient = DoubleArray(attentionDimensions)
        var scoreBiasGradient = 0.0

        val contextGradients = Array(state.candidateIdentities.size) { DoubleArray(attentionDimensions) }
        val queryGradients = Array(state.candidateIdentities.size) { DoubleArray(attentionDimensions) }

        state.candidateIdentities.indices.forEach { candidateIndex ->
            val scoreGradient = scoreGradients[candidateIndex]
            scoreBiasGradient += scoreGradient
            for (dimension in 0 until attentionDimensions) {
                scoreContextHeadGradient[dimension] += state.contexts[candidateIndex][dimension] * scoreGradient
                scoreQueryHeadGradient[dimension] += state.queries[candidateIndex][dimension] * scoreGradient
                contextGradients[candidateIndex][dimension] += scoreGradient * scoreContextHead[dimension]
                queryGradients[candidateIndex][dimension] += scoreGradient * scoreQueryHead[dimension]
            }
        }

        for (dimension in 0 until attentionDimensions) {
            valueContextHeadGradient[dimension] = state.contexts[targetIndex][dimension] * valueGradient
            valueQueryHeadGradient[dimension] = state.queries[targetIndex][dimension] * valueGradient
            contextGradients[targetIndex][dimension] += valueGradient * valueContextHead[dimension]
            queryGradients[targetIndex][dimension] += valueGradient * valueQueryHead[dimension]
        }

        val scale = 1.0 / sqrt(attentionDimensions.toDouble())
        val keyGradients = Array(state.historyInputs.size) { DoubleArray(attentionDimensions) }
        val projectedValueGradients = Array(state.historyInputs.size) { DoubleArray(attentionDimensions) }

        state.weights.indices.forEach { candidateIndex ->
            val attentionGradient = DoubleArray(state.historyInputs.size) { keyIndex ->
                dot(contextGradients[candidateIndex], state.values[keyIndex])
            }
            val weightedAttentionGradient = state.weights[candidateIndex].indices.sumOf { keyIndex ->
                state.weights[candidateIndex][keyIndex] * attentionGradient[keyIndex]
            }
            val attentionScoreGradient = DoubleArray(state.historyInputs.size) { keyIndex ->
                state.weights[candidateIndex][keyIndex] *
                    (attentionGradient[keyIndex] - weightedAttentionGradient)
            }

            state.historyInputs.indices.forEach { keyIndex ->
                for (dimension in 0 until attentionDimensions) {
                    queryGradients[candidateIndex][dimension] +=
                        attentionScoreGradient[keyIndex] * state.keys[keyIndex][dimension] * scale
                    keyGradients[keyIndex][dimension] +=
                        attentionScoreGradient[keyIndex] * state.queries[candidateIndex][dimension] * scale
                    projectedValueGradients[keyIndex][dimension] +=
                        state.weights[candidateIndex][keyIndex] * contextGradients[candidateIndex][dimension]
                }
            }
        }

        val queryProjectionGradient = Array(input.dimensions) { DoubleArray(attentionDimensions) }
        state.candidateInputs.indices.forEach { candidateIndex ->
            state.candidateInputs[candidateIndex].indices.forEach { inputDimension ->
                for (dimension in 0 until attentionDimensions) {
                    queryProjectionGradient[inputDimension][dimension] +=
                        state.candidateInputs[candidateIndex][inputDimension] * queryGradients[candidateIndex][dimension]
                }
            }
        }

        val keyProjectionGradient = Array(input.dimensions) { DoubleArray(attentionDimensions) }
        val valueProjectionGradient = Array(input.dimensions) { DoubleArray(attentionDimensions) }
        state.historyInputs.indices.forEach { position ->
            state.historyInputs[position].indices.forEach { inputDimension ->
                for (dimension in 0 until attentionDimensions) {
                    val inputValue = state.historyInputs[position][inputDimension]
                    keyProjectionGradient[inputDimension][dimension] +=
                        inputValue * keyGradients[position][dimension]
                    valueProjectionGradient[inputDimension][dimension] +=
                        inputValue * projectedValueGradients[position][dimension]
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
        for (dimension in 0 until attentionDimensions) {
            scoreContextHead[dimension] -= step * clip(scoreContextHeadGradient[dimension])
            scoreQueryHead[dimension] -= step * clip(scoreQueryHeadGradient[dimension])
            valueContextHead[dimension] -= step * clip(valueContextHeadGradient[dimension])
            valueQueryHead[dimension] -= step * clip(valueQueryHeadGradient[dimension])
        }
        scoreBias -= step * clip(scoreBiasGradient)
        valueBias -= step * clip(valueGradient)
        trainingExampleCount++
    }

    private fun ensureInitialized(input: Representation) {
        require(input.positions > 0) { "candidate attention requires at least one sensory position" }
        require(input.dimensions > signalEmbeddingDimensions) {
            "input width must contain signal identity plus sensory features"
        }
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
        scoreContextHead = DoubleArray(attentionDimensions) { randomWeight() }
        scoreQueryHead = DoubleArray(attentionDimensions) { randomWeight() }
        valueContextHead = DoubleArray(attentionDimensions) { randomWeight() }
        valueQueryHead = DoubleArray(attentionDimensions) { randomWeight() }
    }

    private fun forwardState(input: Representation): ForwardState {
        val historyInputs = input.map(::normalizedInput)
        val candidateIdentities = candidateIdentities(input)
        val candidateInputs = candidateIdentities.map { identity ->
            DoubleArray(input.dimensions) { dimension ->
                if (dimension < signalEmbeddingDimensions) identity[dimension] else 0.0
            }
        }
        val queries = candidateInputs.map { project(it, queryProjection) }
        val keys = historyInputs.map { project(it, keyProjection) }
        val values = historyInputs.map { project(it, valueProjection) }
        val result = attention.apply(
            queries = Representation.from(queries.map(Embedding::from)),
            keys = Representation.from(keys.map(Embedding::from)),
            values = Representation.from(values.map(Embedding::from)),
        )
        val contexts = result.output.map(Embedding::toDoubleArray)
        val logits = DoubleArray(candidateIdentities.size) { candidateIndex ->
            scoreBias +
                dot(contexts[candidateIndex], scoreContextHead) +
                dot(queries[candidateIndex], scoreQueryHead)
        }
        val valueOutputs = DoubleArray(candidateIdentities.size) { candidateIndex ->
            valueBias +
                dot(contexts[candidateIndex], valueContextHead) +
                dot(queries[candidateIndex], valueQueryHead)
        }

        return ForwardState(
            historyInputs = historyInputs,
            candidateIdentities = candidateIdentities,
            candidateInputs = candidateInputs,
            queries = queries,
            keys = keys,
            values = values,
            weights = result.weights,
            contexts = contexts,
            logits = logits,
            valueOutputs = valueOutputs,
        )
    }

    private fun candidateIdentities(input: Representation): List<DoubleArray> = buildList {
        input.forEach { embedding ->
            val identity = DoubleArray(signalEmbeddingDimensions) { dimension -> embedding[dimension] }
            if (none { existing -> existing.contentEquals(identity) }) add(identity)
        }
    }

    /** Sensory positions place the scalar value immediately after the signal-identity embedding. */
    private fun normalizedInput(embedding: Embedding): DoubleArray =
        embedding.toDoubleArray().also { values ->
            val valueIndex = signalEmbeddingDimensions
            val value = values[valueIndex]
            values[valueIndex] =
                if (value == 0.0) 0.0 else kotlin.math.sign(value) * ln1p(kotlin.math.abs(value))
        }

    private fun project(input: DoubleArray, matrix: Array<DoubleArray>): DoubleArray =
        DoubleArray(attentionDimensions) { outputDimension ->
            input.indices.sumOf { inputDimension -> input[inputDimension] * matrix[inputDimension][outputDimension] }
        }

    private fun softmax(scores: DoubleArray): DoubleArray {
        val maxScore = scores.maxOrNull() ?: error("softmax requires candidate scores")
        val exponentials = DoubleArray(scores.size) { index -> exp(scores[index] - maxScore) }
        val total = exponentials.sum()
        return DoubleArray(scores.size) { index -> exponentials[index] / total }
    }

    private fun identityDistance(left: DoubleArray, right: DoubleArray): Double =
        sqrt(left.indices.sumOf { index ->
            val delta = left[index] - right[index]
            delta * delta
        } / left.size.toDouble())

    private fun dot(left: DoubleArray, right: DoubleArray): Double =
        left.indices.sumOf { left[it] * right[it] }

    private fun randomWeight(): Double = random.nextDouble(-0.08, 0.08)

    private fun clip(value: Double): Double = value.coerceIn(-5.0, 5.0)

    private data class ForwardState(
        val historyInputs: List<DoubleArray>,
        val candidateIdentities: List<DoubleArray>,
        val candidateInputs: List<DoubleArray>,
        val queries: List<DoubleArray>,
        val keys: List<DoubleArray>,
        val values: List<DoubleArray>,
        val weights: List<DoubleArray>,
        val contexts: List<DoubleArray>,
        val logits: DoubleArray,
        val valueOutputs: DoubleArray,
    )
}
