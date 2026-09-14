package no.skasti.skynvaettr.representation

/**
 * Fixed-width numeric representation used by learned and deterministic model components.
 *
 * [Embedding] is deliberately agnostic about how the vector was produced. It may represent lexical
 * identity, a learned hidden state, a projected query/key/value vector, or another model-specific
 * representation.
 */
class Embedding private constructor(
    private val components: DoubleArray,
) {
    val dimensions: Int
        get() = components.size

    operator fun get(index: Int): Double = components[index]

    /** Returns an independent copy that callers may mutate freely. */
    fun toDoubleArray(): DoubleArray = components.clone()

    override fun equals(other: Any?): Boolean =
        other is Embedding && components.contentEquals(other.components)

    override fun hashCode(): Int = components.contentHashCode()

    override fun toString(): String = components.joinToString(prefix = "Embedding(", postfix = ")")

    companion object {
        fun of(vararg components: Double): Embedding = from(components)

        fun from(components: DoubleArray): Embedding {
            require(components.isNotEmpty()) { "embedding must have at least one dimension" }
            require(components.all(Double::isFinite)) { "embedding components must be finite" }
            return Embedding(components.clone())
        }
    }
}

/**
 * Produces a fixed-width [Embedding] from values of type [T].
 *
 * Implementations may be deterministic or learned. The abstraction intentionally does not prescribe
 * how training happens.
 */
interface Embedder<in T> {
    val dimensions: Int

    fun embed(value: T): Embedding
}
