package no.skasti.skynvaettr.attention

import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.training.LinearForwardPass
import no.skasti.skynvaettr.training.LinearParameters
import no.skasti.skynvaettr.runtime.SingleSlotPort
import no.skasti.skynvaettr.topology.Node
import no.skasti.skynvaettr.topology.Port

/** A replaceable transformation from one representation to another. */
fun interface RepresentationProjection {
    fun apply(input: Representation): Representation
}

/** Keeps the input representation unchanged. */
object IdentityRepresentationProjection : RepresentationProjection {
    override fun apply(input: Representation): Representation = input
}

/**
 * Applies one learned or configured linear transform independently to every position.
 *
 * [weights] is indexed as output dimension, then input dimension. The input width is therefore
 * `weights.first().size`, and the output width is `weights.size`.
 */
interface TrainableRepresentationProjection : RepresentationProjection {
    val inputDimensions: Int
    val outputDimensions: Int
    fun parametersSnapshot(): LinearParameters
    fun replaceParameters(parameters: LinearParameters)
    fun recordForward(input: Representation, output: Representation, revision: Long): LinearForwardPass
}

/**
 * Linear transform shared by static and trainable projection nodes.
 * Replacing parameters never changes the input or output shape.
 * Reads numeric Embeddings and returns a numeric representation; alternative backend autograd
 * history is not propagated by this implementation.
 */
class LinearRepresentationProjection(
    weights: List<DoubleArray>,
    bias: DoubleArray = DoubleArray(weights.size),
) : TrainableRepresentationProjection {
    private var parameters = LinearParameters(weights, bias)

    constructor(parameters: LinearParameters) : this(parameters.weightsCopy(), parameters.biasCopy())

    override val inputDimensions: Int = parameters.inputDimensions
    override val outputDimensions: Int = parameters.outputDimensions

    override fun parametersSnapshot(): LinearParameters = parameters

    override fun replaceParameters(parameters: LinearParameters) {
        require(parameters.inputDimensions == inputDimensions &&
            parameters.outputDimensions == outputDimensions) { "projection parameter shape mismatch" }
        this.parameters = parameters
    }

    override fun apply(input: Representation): Representation {
        require(input.dimensions == inputDimensions) {
            "linear projection expects $inputDimensions dimensions, got ${input.dimensions}"
        }
        val snapshot = parameters
        return Representation.from(input.map { embedding ->
            Embedding.from(DoubleArray(outputDimensions) { output ->
                snapshot.offset(output) + (0 until inputDimensions).sumOf { dimension ->
                    snapshot.weight(output, dimension) * embedding[dimension]
                }
            })
        }.toList())
    }

    override fun recordForward(input: Representation, output: Representation, revision: Long): LinearForwardPass =
        LinearForwardPass(input, output, revision, parametersSnapshot())
}

/** Base node for the query, key, and value projection stages. */
open class ProjectionNode(
    private val role: String,
    protected val projection: RepresentationProjection = IdentityRepresentationProjection,
    override val name: String = role,
) : Node {
    val input = SingleSlotPort<Representation>("$role-input")
    val output = SingleSlotPort<Representation>(role)

    override val ports: List<Port<*>> = listOf(input, output)

    init {
        input.onReceive += { processIfReady() }
    }

    protected open fun onProjected(input: Representation, output: Representation) {}

    private fun processIfReady() {
        val value = input.pending ?: return
        val projected = projection.apply(value)
        input.clear()
        onProjected(value, projected)
        output.emit(projected)
    }
}

/** Produces query representations for an [AttentionNode]. */
class QueryNode(
    projection: RepresentationProjection = IdentityRepresentationProjection,
    name: String = "Query",
) : ProjectionNode("q", projection, name)

/** Produces key representations for an [AttentionNode]. */
class KeyNode(
    projection: RepresentationProjection = IdentityRepresentationProjection,
    name: String = "Key",
) : ProjectionNode("k", projection, name)

/** Produces value representations for an [AttentionNode]. */
class ValueNode(
    projection: RepresentationProjection = IdentityRepresentationProjection,
    name: String = "Value",
) : ProjectionNode("v", projection, name)
