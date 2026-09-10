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
 * Token values are encoded exactly as supplied. This encoder does not perform Unicode
 * normalization, case folding, or other canonicalization; those transformations belong upstream
 * in tokenization when desired. Canonically equivalent strings with different UTF-8 byte sequences
 * therefore intentionally produce different representations here.
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
            // Use the high sign bit rather than a low bit also consumed by bucket selection.
            // This keeps signed feature hashing from tying a coordinate's sign to its index parity.
            target[index] += if (hash < 0) -1.0 else 1.0
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
