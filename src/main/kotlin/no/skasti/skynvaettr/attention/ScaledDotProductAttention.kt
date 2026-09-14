package no.skasti.skynvaettr.attention

import kotlin.math.exp
import kotlin.math.sqrt
import no.skasti.skynvaettr.representation.Embedding

/**
 * Result from one attention operation.
 *
 * [outputs] contains one context embedding per query. [weights] is row-normalized: row i describes
 * how query i distributes attention across the keys.
 */
class AttentionResult internal constructor(
    outputs: List<Embedding>,
    weights: List<DoubleArray>,
) {
    val outputs: List<Embedding> = outputs.toList()
    val weights: List<DoubleArray> = weights.map(DoubleArray::clone)
}

/**
 * Dependency-free scaled dot-product attention over already projected query/key/value embeddings.
 *
 * This class deliberately does not own Q/K/V projection matrices or training state. A model may
 * learn those projections and pass their outputs here. Keeping the mathematical attention primitive
 * separate prevents core from prescribing a particular neural-network implementation or trainer.
 */
class ScaledDotProductAttention {
    fun apply(
        queries: List<Embedding>,
        keys: List<Embedding>,
        values: List<Embedding>,
    ): AttentionResult {
        require(queries.isNotEmpty()) { "queries must not be empty" }
        require(keys.isNotEmpty()) { "keys must not be empty" }
        require(keys.size == values.size) { "keys and values must have the same number of entries" }

        val queryKeyDimensions = queries.first().dimensions
        val valueDimensions = values.first().dimensions
        require(queryKeyDimensions > 0)
        require(valueDimensions > 0)
        require(queries.all { it.dimensions == queryKeyDimensions }) {
            "all queries must have the same dimensions"
        }
        require(keys.all { it.dimensions == queryKeyDimensions }) {
            "query and key dimensions must match"
        }
        require(values.all { it.dimensions == valueDimensions }) {
            "all values must have the same dimensions"
        }

        val scale = 1.0 / sqrt(queryKeyDimensions.toDouble())
        val weights =
            queries.map { query ->
                softmax(
                    DoubleArray(keys.size) { index ->
                        dot(query, keys[index]) * scale
                    },
                )
            }

        val outputs =
            weights.map { row ->
                Embedding.from(
                    DoubleArray(valueDimensions) { dimension ->
                        values.indices.sumOf { index -> row[index] * values[index][dimension] }
                    },
                )
            }

        return AttentionResult(outputs, weights)
    }

    /** Convenience form for self-attention when Q, K and V are the same representation. */
    fun selfAttention(inputs: List<Embedding>): AttentionResult = apply(inputs, inputs, inputs)

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
