package no.skasti.skynvaettr.attention

import kotlin.math.exp
import kotlin.math.sqrt
import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation

/**
 * Result from one attention operation.
 *
 * [output] contains one context position per query. [weights] is row-normalized: row i describes
 * how query i distributes attention across the keys.
 */
class AttentionResult internal constructor(
    val output: Representation,
    weights: List<DoubleArray>,
) {
    val weights: List<DoubleArray> = weights.map(DoubleArray::clone)
}

/**
 * Dependency-free scaled dot-product attention over already projected query/key/value representations.
 *
 * This class deliberately does not own Q/K/V projection matrices or training state. A model may
 * learn those projections and pass their outputs here. Keeping the mathematical attention primitive
 * separate prevents core from prescribing a particular neural-network implementation or trainer.
 */
class ScaledDotProductAttention : Attention {
    override fun apply(
        queries: Representation,
        keys: Representation,
        values: Representation,
    ): AttentionResult {
        require(keys.positions == values.positions) {
            "keys and values must have the same number of positions"
        }
        require(queries.dimensions == keys.dimensions) {
            "query and key dimensions must match"
        }

        val scale = 1.0 / sqrt(queries.dimensions.toDouble())
        val weights =
            queries.map { query ->
                softmax(
                    DoubleArray(keys.positions) { index ->
                        dot(query, keys[index]) * scale
                    },
                )
            }

        val outputs =
            weights.map { row ->
                Embedding.from(
                    DoubleArray(values.dimensions) { dimension ->
                        (0 until values.positions).sumOf { index -> row[index] * values[index][dimension] }
                    },
                )
            }

        return AttentionResult(Representation.from(outputs), weights)
    }

    private fun dot(a: Embedding, b: Embedding): Double {
        require(a.dimensions == b.dimensions)
        return (0 until a.dimensions).sumOf { dimension -> a[dimension] * b[dimension] }
    }

    private fun softmax(scores: DoubleArray): DoubleArray {
        val max = scores.maxOrNull() ?: error("softmax requires scores")
        val exponentials = DoubleArray(scores.size) { index -> exp(scores[index] - max) }
        val total = exponentials.sum()
        return DoubleArray(scores.size) { index -> exponentials[index] / total }
    }
}
