package no.skasti.skynvaettr.tokenization

import java.nio.charset.StandardCharsets
import kotlin.math.sqrt

/**
 * Source-agnostic compositional baseline for lexical tokens.
 *
 * The encoder projects UTF-8 byte unigrams and adjacent byte bigrams into a fixed-width vector.
 * It does not require a vocabulary lookup, so previously unseen tokens retain a deterministic
 * representation without growing model state or expanding bytes into outer sequence positions.
 *
 * This is deliberately a structural baseline rather than a learned semantic embedding.
 */
class DeterministicByteTokenEncoder(
    override val dimensions: Int = DEFAULT_DIMENSIONS,
    private val projectionsPerFeature: Int = DEFAULT_PROJECTIONS_PER_FEATURE,
) : TokenEncoder {
    init {
        require(dimensions > 0) { "dimensions must be positive" }
        require(projectionsPerFeature > 0) { "projectionsPerFeature must be positive" }
    }

    override fun encode(token: Token): DoubleArray {
        val bytes = token.value.toByteArray(StandardCharsets.UTF_8)
        val vector = DoubleArray(dimensions)

        bytes.forEachIndexed { position, byte ->
            val unsigned = byte.toInt() and 0xff
            project(vector, "u:$unsigned", position)

            if (position + 1 < bytes.size) {
                val next = bytes[position + 1].toInt() and 0xff
                project(vector, "b:$unsigned:$next", position)
            }
        }

        return normalize(vector)
    }

    private fun project(target: DoubleArray, key: String, salt: Int) {
        repeat(projectionsPerFeature) { projection ->
            var hash = key.hashCode() xor ((salt + projection * 31) * GOLDEN_RATIO_HASH)
            hash = hash xor (hash ushr 16)
            val index = Math.floorMod(hash, target.size)
            target[index] += if ((hash and 1) == 0) 1.0 else -1.0
        }
    }

    private fun normalize(vector: DoubleArray): DoubleArray {
        val norm = sqrt(vector.sumOf { it * it })
        if (norm == 0.0) return vector
        return DoubleArray(vector.size) { vector[it] / norm }
    }

    private companion object {
        const val DEFAULT_DIMENSIONS = 64
        const val DEFAULT_PROJECTIONS_PER_FEATURE = 2
        const val GOLDEN_RATIO_HASH = -1640531527
    }
}
