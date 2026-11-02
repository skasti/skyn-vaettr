package no.skasti.skynvaettr.training

import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.runtime.SingleSlotPort
import no.skasti.skynvaettr.topology.Node
import no.skasti.skynvaettr.topology.Port

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
 * Trainable, position-wise linear node with observable forward passes and revision-checked
 * parameter replacement. The node never chooses targets, loss, optimizer or update schedule.
 * A trainer composes recorded passes and sends replacements through [parameterUpdates].
 */
class TrainableLinearNode(
    initialParameters: LinearParameters,
    override val name: String = "Trainable linear",
) : Node {
    val input = SingleSlotPort<Representation>("$name-input")
    val output = SingleSlotPort<Representation>("$name-output")
    val forwardPasses = SingleSlotPort<LinearForwardPass>("$name-forward-pass")
    val snapshotRequests = SingleSlotPort<Unit>("$name-snapshot-request")
    val parameterSnapshots = SingleSlotPort<LinearParameterSnapshot>("$name-parameters")
    val parameterUpdates = SingleSlotPort<LinearParameterUpdate>("$name-parameter-update")

    override val ports: List<Port<*>> =
        listOf(input, output, forwardPasses, snapshotRequests, parameterSnapshots, parameterUpdates)

    private var parameters: LinearParameters = initialParameters
    var revision: Long = 0
        private set

    init {
        require(name.isNotBlank())
        input.onReceive += inputReceiver@ {
            val value = input.pending ?: return@inputReceiver
            require(value.dimensions == parameters.inputDimensions) { "input dimension mismatch" }
            val outputs = value.map { embedding ->
                Embedding.from(DoubleArray(parameters.outputDimensions) { o ->
                    parameters.offset(o) + (0 until parameters.inputDimensions).sumOf { i ->
                        parameters.weight(o, i) * embedding[i]
                    }
                })
            }.toList()
            val pass = LinearForwardPass(value, Representation.from(outputs), revision, parameters)
            input.clear()
            output.emit(pass.output)
            forwardPasses.emit(pass)
        }
        snapshotRequests.onReceive += {
            if (snapshotRequests.pending != null) {
                snapshotRequests.clear()
                parameterSnapshots.emit(LinearParameterSnapshot(revision, parameters))
            }
        }
        parameterUpdates.onReceive += updateReceiver@ {
            val change = parameterUpdates.pending ?: return@updateReceiver
            check(change.expectedRevision == revision) { "stale parameters for $name" }
            require(change.parameters.inputDimensions == parameters.inputDimensions &&
                change.parameters.outputDimensions == parameters.outputDimensions) { "parameter shape mismatch" }
            val nextRevision = Math.addExact(revision, 1L)
            parameters = change.parameters
            revision = nextRevision
            parameterUpdates.clear()
            parameterSnapshots.emit(LinearParameterSnapshot(revision, parameters))
        }
    }
}
