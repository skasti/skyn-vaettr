package no.skasti.skynvaettr.attention

import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation
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
class LinearRepresentationProjection(
    weights: List<DoubleArray>,
    bias: DoubleArray = DoubleArray(weights.size),
) : RepresentationProjection {
    private val weights: List<DoubleArray> = weights.map(DoubleArray::clone)
    private val bias: DoubleArray = bias.clone()

    init {
        require(this.weights.isNotEmpty()) { "linear projection must have at least one output dimension" }
        val inputDimensions = this.weights.first().size
        require(inputDimensions > 0) { "linear projection must have at least one input dimension" }
        require(this.weights.all { it.size == inputDimensions }) {
            "all linear projection rows must have the same input dimension"
        }
        require(this.bias.size == this.weights.size) {
            "linear projection bias must match the output dimension"
        }
        require(this.weights.all { row -> row.all(Double::isFinite) } && this.bias.all(Double::isFinite)) {
            "linear projection parameters must be finite"
        }
    }

    val inputDimensions: Int
        get() = weights.first().size

    val outputDimensions: Int
        get() = weights.size

    override fun apply(input: Representation): Representation {
        require(input.dimensions == inputDimensions) {
            "linear projection expects $inputDimensions dimensions, got ${input.dimensions}"
        }
        return Representation.from(
            input.map { embedding ->
                Embedding.from(
                    DoubleArray(outputDimensions) { output ->
                        bias[output] + (0 until inputDimensions).sumOf { dimension ->
                            weights[output][dimension] * embedding[dimension]
                        }
                    },
                )
            }.toList(),
        )
    }
}

/** Base node for the query, key, and value projection stages. */
open class ProjectionNode(
    private val role: String,
    private val projection: RepresentationProjection = IdentityRepresentationProjection,
    override val name: String = role,
) : Node {
    val input = SingleSlotPort<Representation>("$role-input")
    val output = SingleSlotPort<Representation>(role)

    override val ports: List<Port<*>> = listOf(input, output)

    init {
        input.onReceive += { processIfReady() }
    }

    private fun processIfReady() {
        val value = input.pending ?: return
        val projected = projection.apply(value)
        input.clear()
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
