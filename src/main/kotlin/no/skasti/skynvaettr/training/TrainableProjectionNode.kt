package no.skasti.skynvaettr.training

import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.attention.LinearRepresentationProjection
import no.skasti.skynvaettr.attention.ProjectionNode
import no.skasti.skynvaettr.attention.TrainableRepresentationProjection
import no.skasti.skynvaettr.runtime.SingleSlotPort

/**
 * Immutable value snapshot of a position-wise linear transform. Arrays are defensively copied.
 * The role (query/key/value/decoder/other) is deliberately not part of this contract.
 */
class LinearParameters(weights: List<DoubleArray>, bias: DoubleArray) {
    private val rows = weights.map(DoubleArray::clone)
    private val offsets = bias.clone()

    val inputDimensions: Int
    val outputDimensions: Int get() = rows.size

    init {
        require(rows.isNotEmpty() && rows[0].isNotEmpty()) { "parameter dimensions must be positive" }
        inputDimensions = rows[0].size
        require(rows.all { it.size == inputDimensions && it.all(Double::isFinite) }) { "invalid weights" }
        require(offsets.size == rows.size && offsets.all(Double::isFinite)) { "invalid bias" }
    }

    fun weightsCopy(): List<DoubleArray> = rows.map(DoubleArray::clone)
    fun biasCopy(): DoubleArray = offsets.clone()

    internal fun weight(out: Int, input: Int): Double = rows[out][input]
    internal fun offset(out: Int): Double = offsets[out]

    fun descend(gradient: LinearGradients, learningRate: Double): LinearParameters {
        require(learningRate.isFinite() && learningRate > 0) { "learning rate must be finite and positive" }
        require(gradient.weights.size == outputDimensions &&
            gradient.weights.all { it.size == inputDimensions } &&
            gradient.bias.size == outputDimensions) { "gradient shape mismatch" }
        return LinearParameters(
            rows.indices.map { o ->
                DoubleArray(inputDimensions) { i -> rows[o][i] - learningRate * gradient.weights[o][i] }
            },
            DoubleArray(outputDimensions) { o -> offsets[o] - learningRate * gradient.bias[o] },
        )
    }

    companion object {
        fun identity(dimensions: Int): LinearParameters {
            require(dimensions > 0)
            return LinearParameters(
                List(dimensions) { o -> DoubleArray(dimensions) { i -> if (i == o) 1.0 else 0.0 } },
                DoubleArray(dimensions),
            )
        }
    }
}

/** Gradients for an entire recorded linear forward pass, summed across its positions. */
class LinearGradients(weights: List<DoubleArray>, bias: DoubleArray, val input: Representation) {
    val weights: List<DoubleArray> = weights.map(DoubleArray::clone)
    val bias: DoubleArray = bias.clone()
}

/** A completed execution retains exactly the parameters used, even after later node updates. */
class LinearForwardPass(
    val input: Representation,
    val output: Representation,
    val revision: Long,
    private val parameters: LinearParameters,
) {
    fun backward(outputGradient: Representation): LinearGradients {
        require(outputGradient.positions == output.positions &&
            outputGradient.dimensions == output.dimensions) { "output gradient shape mismatch" }
        val weights = List(parameters.outputDimensions) { DoubleArray(parameters.inputDimensions) }
        val bias = DoubleArray(parameters.outputDimensions)
        val inputGradients = mutableListOf<Embedding>()
        for (position in 0 until input.positions) {
            val inputGradient = DoubleArray(parameters.inputDimensions)
            for (o in 0 until parameters.outputDimensions) {
                val g = outputGradient[position][o]
                bias[o] += g
                for (i in 0 until parameters.inputDimensions) {
                    weights[o][i] += g * input[position][i]
                    inputGradient[i] += g * parameters.weight(o, i)
                }
            }
            inputGradients += Embedding.from(inputGradient)
        }
        return LinearGradients(weights, bias, Representation.from(inputGradients))
    }
}

data class LinearParameterSnapshot(val revision: Long, val parameters: LinearParameters)
data class LinearParameterUpdate(val expectedRevision: Long, val parameters: LinearParameters)

/**
 * A projection node with observable differentiable passes and guarded parameter replacement.
 * The parent owns input/output processing; the projection owns the numerical transformation.
 * The current parameter/gradient contract supports linear projections only.
 */
class TrainableProjectionNode(
    private val trainableProjection: TrainableRepresentationProjection,
    override val name: String = "Trainable projection",
) : ProjectionNode(name, trainableProjection, name) {
    constructor(initialParameters: LinearParameters, name: String = "Trainable projection") :
        this(LinearRepresentationProjection(initialParameters), name)

    val forwardPasses = SingleSlotPort<LinearForwardPass>("$name-forward-pass")
    val snapshotRequests = SingleSlotPort<Unit>("$name-snapshot-request")
    val parameterSnapshots = SingleSlotPort<LinearParameterSnapshot>("$name-parameters")
    val parameterUpdates = SingleSlotPort<LinearParameterUpdate>("$name-parameter-update")

    override val ports = super.ports + listOf(
        forwardPasses, snapshotRequests, parameterSnapshots, parameterUpdates,
    )

    var revision: Long = 0
        private set

    init {
        require(name.isNotBlank())
        snapshotRequests.onReceive += {
            if (snapshotRequests.pending != null) {
                snapshotRequests.clear()
                parameterSnapshots.emit(LinearParameterSnapshot(revision, trainableProjection.parametersSnapshot()))
            }
        }
        parameterUpdates.onReceive += updateReceiver@ {
            val change = parameterUpdates.pending ?: return@updateReceiver
            check(change.expectedRevision == revision) { "stale parameters for $name" }
            require(change.parameters.inputDimensions == trainableProjection.inputDimensions &&
                change.parameters.outputDimensions == trainableProjection.outputDimensions) {
                "parameter shape mismatch"
            }
            val nextRevision = Math.addExact(revision, 1L)
            trainableProjection.replaceParameters(change.parameters)
            revision = nextRevision
            parameterUpdates.clear()
            parameterSnapshots.emit(LinearParameterSnapshot(revision, trainableProjection.parametersSnapshot()))
        }
    }

    override fun onProjected(input: Representation, output: Representation) {
        forwardPasses.emit(trainableProjection.recordForward(input, output, revision))
    }
}
