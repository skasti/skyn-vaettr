package no.skasti.skynvaettr.models

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.ln1p
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random
import no.skasti.skynvaettr.attention.ScaledDotProductAttention
import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation

/**
 * Small dependency-free candidate-conditioned attention model for next-transition predictions.
 *
 * Every distinct signal identity present in the sensory history becomes one candidate query. The
 * same learned Q projection is reused for every candidate while K/V are projected from the complete
 * sensory history. A shared nonlinear readout over `[candidate query ; attended context]` produces
 * one next-signal logit and candidate-specific value per signal.
 *
 * Training uses cross-entropy over candidate logits for target-signal identity and a numeric value
 * loss only for the observed target candidate.
 */
class LearnedAttentionTransitionModel(
    private val signalEmbeddingDimensions: Int,
    private val attentionDimensions: Int = 8,
    private val hiddenDimensions: Int = 16,
    private val learningRate: Double = 0.01,
    seed: Int = 37,
) : TrainableModel {
    private val attention = ScaledDotProductAttention()
    private val random = Random(seed)

    private var inputDimensions: Int? = null
    private lateinit var queryProjection: Array<DoubleArray>
    private lateinit var keyProjection: Array<DoubleArray>
    private lateinit var valueProjection: Array<DoubleArray>
    private lateinit var hiddenProjection: Array<DoubleArray>
    private lateinit var hiddenBias: DoubleArray
    private lateinit var scoreHead: DoubleArray
    private lateinit var valueHead: DoubleArray
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
        require(hiddenDimensions > 0)
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

        val scoreHeadGradient = DoubleArray(hiddenDimensions)
        val valueHeadGradient = DoubleArray(hiddenDimensions)
        val hiddenProjectionGradient = Array(attentionDimensions * 2) { DoubleArray(hiddenDimensions) }
        val hiddenBiasGradient = DoubleArray(hiddenDimensions)
        var scoreBiasGradient = 0.0

        val queryGradients = Array(state.candidateIdentities.size) { DoubleArray(attentionDimensions) }
        val contextGradients = Array(state.candidateIdentities.size) { DoubleArray(attentionDimensions) }

        state.candidateIdentities.indices.forEach { candidateIndex ->
            val scoreGradient = scoreGradients[candidateIndex]
            val candidateValueGradient = if (candidateIndex == targetIndex) valueGradient else 0.0
            scoreBiasGradient += scoreGradient

            val hiddenGradient = DoubleArray(hiddenDimensions) { hiddenDimension ->
                scoreGradient * scoreHead[hiddenDimension] +
                    candidateValueGradient * valueHead[hiddenDimension]
            }
            val hiddenPreActivationGradient = DoubleArray(hiddenDimensions) { hiddenDimension ->
                val activation = state.hidden[candidateIndex][hiddenDimension]
                hiddenGradient[hiddenDimension] * (1.0 - activation * activation)
            }

            for (hiddenDimension in 0 until hiddenDimensions) {
                scoreHeadGradient[hiddenDimension] += state.hidden[candidateIndex][hiddenDimension] * scoreGradient
                if (candidateIndex == targetIndex) {
                    valueHeadGradient[hiddenDimension] +=
                        state.hidden[candidateIndex][hiddenDimension] * valueGradient
                }
                hiddenBiasGradient[hiddenDimension] += hiddenPreActivationGradient[hiddenDimension]
            }

            val featureGradient = DoubleArray(attentionDimensions * 2)
            state.features[candidateIndex].indices.forEach { featureDimension ->
                for (hiddenDimension in 0 until hiddenDimensions) {
                    hiddenProjectionGradient[featureDimension][hiddenDimension] +=
                        state.features[candidateIndex][featureDimension] * hiddenPreActivationGradient[hiddenDimension]
                    featureGradient[featureDimension] +=
                        hiddenProjection[featureDimension][hiddenDimension] * hiddenPreActivationGradient[hiddenDimension]
                }
            }
            for (dimension in 0 until attentionDimensions) {
                queryGradients[candidateIndex][dimension] += featureGradient[dimension]
                contextGradients[candidateIndex][dimension] += featureGradient[attentionDimensions + dimension]
            }
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
        for (featureDimension in hiddenProjection.indices) {
            for (hiddenDimension in 0 until hiddenDimensions) {
                hiddenProjection[featureDimension][hiddenDimension] -=
                    step * clip(hiddenProjectionGradient[featureDimension][hiddenDimension])
            }
        }
        for (hiddenDimension in 0 until hiddenDimensions) {
            hiddenBias[hiddenDimension] -= step * clip(hiddenBiasGradient[hiddenDimension])
            scoreHead[hiddenDimension] -= step * clip(scoreHeadGradient[hiddenDimension])
            valueHead[hiddenDimension] -= step * clip(valueHeadGradient[hiddenDimension])
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
        hiddenProjection = Array(attentionDimensions * 2) {
            DoubleArray(hiddenDimensions) { randomWeight() }
        }
        hiddenBias = DoubleArray(hiddenDimensions)
        scoreHead = DoubleArray(hiddenDimensions) { randomWeight() }
        valueHead = DoubleArray(hiddenDimensions) { randomWeight() }
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
        val features = candidateIdentities.indices.map { candidateIndex ->
            queries[candidateIndex] + contexts[candidateIndex]
        }
        val hidden = features.map { feature ->
            DoubleArray(hiddenDimensions) { hiddenDimension ->
                var value = hiddenBias[hiddenDimension]
                feature.indices.forEach { featureDimension ->
                    value += feature[featureDimension] * hiddenProjection[featureDimension][hiddenDimension]
                }
                tanh(value)
            }
        }
        val logits = DoubleArray(candidateIdentities.size) { candidateIndex ->
            scoreBias + dot(hidden[candidateIndex], scoreHead)
        }
        val valueOutputs = DoubleArray(candidateIdentities.size) { candidateIndex ->
            valueBias + dot(hidden[candidateIndex], valueHead)
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
            features = features,
            hidden = hidden,
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
        val features: List<DoubleArray>,
        val hidden: List<DoubleArray>,
        val logits: DoubleArray,
        val valueOutputs: DoubleArray,
    )
}
