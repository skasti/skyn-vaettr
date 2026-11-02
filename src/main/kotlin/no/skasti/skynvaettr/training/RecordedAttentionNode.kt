package no.skasti.skynvaettr.training

import kotlin.math.sqrt
import no.skasti.skynvaettr.attention.Attention
import no.skasti.skynvaettr.attention.ScaledDotProductAttention
import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.runtime.SingleSlotPort
import no.skasti.skynvaettr.topology.Node
import no.skasti.skynvaettr.topology.Port

/** Per-input gradients for one recorded scaled-dot-product attention operation. */
data class AttentionInputGradients(
    val query: Representation,
    val key: Representation,
    val value: Representation,
)

/**
 * A completed scaled-dot-product attention execution, including its actual softmax weights.
 * Backward computes the analytic vector-Jacobian product without modifying any node.
 */
class AttentionForwardPass(
    val queries: Representation,
    val keys: Representation,
    val values: Representation,
    val output: Representation,
    weights: List<DoubleArray>,
) {
    private val weights = weights.map(DoubleArray::clone)

    init {
        require(queries.dimensions == keys.dimensions)
        require(keys.positions == values.positions)
        require(output.positions == queries.positions && output.dimensions == values.dimensions)
        require(this.weights.size == queries.positions &&
            this.weights.all { it.size == keys.positions && it.all(Double::isFinite) })
    }

    fun backward(outputGradient: Representation): AttentionInputGradients {
        require(outputGradient.positions == output.positions &&
            outputGradient.dimensions == output.dimensions) { "attention gradient shape mismatch" }
        val qGrad = Array(queries.positions) { DoubleArray(queries.dimensions) }
        val kGrad = Array(keys.positions) { DoubleArray(keys.dimensions) }
        val vGrad = Array(values.positions) { DoubleArray(values.dimensions) }
        val scale = 1.0 / sqrt(queries.dimensions.toDouble())

        for (i in 0 until queries.positions) {
            val weightGradient = DoubleArray(keys.positions) { j ->
                (0 until values.dimensions).sumOf { d ->
                    outputGradient[i][d] * values[j][d]
                }
            }
            val weightedMean = (0 until keys.positions).sumOf { j ->
                weights[i][j] * weightGradient[j]
            }
            for (j in 0 until keys.positions) {
                for (d in 0 until values.dimensions) {
                    vGrad[j][d] += weights[i][j] * outputGradient[i][d]
                }
                val scoreGradient = weights[i][j] * (weightGradient[j] - weightedMean) * scale
                for (d in 0 until queries.dimensions) {
                    qGrad[i][d] += scoreGradient * keys[j][d]
                    kGrad[j][d] += scoreGradient * queries[i][d]
                }
            }
        }
        return AttentionInputGradients(
            Representation.from(qGrad.map(Embedding::from)),
            Representation.from(kGrad.map(Embedding::from)),
            Representation.from(vGrad.map(Embedding::from)),
        )
    }
}

/**
 * Attention node with an additional observed forward-pass port. Only the scaled-dot-product
 * derivative is supported; other Attention implementations must not be silently differentiated
 * using the wrong mathematical operation.
 */
class RecordedAttentionNode(
    private val implementation: Attention = ScaledDotProductAttention(),
    override val name: String = "Attention",
) : Node {
    val q = SingleSlotPort<Representation>("q")
    val k = SingleSlotPort<Representation>("k")
    val v = SingleSlotPort<Representation>("v")
    val attention = SingleSlotPort<Representation>("attention")
    val forwardPasses = SingleSlotPort<AttentionForwardPass>("attention-forward-pass")
    override val ports: List<Port<*>> = listOf(q, k, v, attention, forwardPasses)

    init {
        require(implementation is ScaledDotProductAttention) {
            "recorded attention backward currently supports ScaledDotProductAttention only"
        }
        listOf(q, k, v).forEach { port ->
            port.onReceive += {
                val queries = q.pending
                val keys = k.pending
                val values = v.pending
                if (queries != null && keys != null && values != null) {
                    val result = implementation.apply(queries, keys, values)
                    val pass = AttentionForwardPass(queries, keys, values, result.output, result.weights)
                    q.clear()
                    k.clear()
                    v.clear()
                    attention.emit(result.output)
                    forwardPasses.emit(pass)
                }
            }
        }
    }
}
