package no.skasti.skynvaettr.representation

/**
 * Backend-independent two-dimensional latent representation exchanged between model components.
 *
 * While valid, a representation has at least one position and one dimension, and every position
 * exposes an [Embedding] of [dimensions] components. Indexing, iteration and [toList] expose the
 * same positions in order. Core assigns no semantics to either axis: positions may represent
 * observations, tokens, time steps, memory slots, or another model-specific structure.
 *
 * This is a logical access contract, not a storage or ownership contract. Implementations specify
 * value stability, aliasing, access cost, thread restrictions and validity/lifetime. In particular,
 * retaining a representation in a port does not extend the lifetime of resources it uses.
 * [NumericRepresentation] provides immutable numeric values with no external resource lifetime.
 */
interface Representation : Iterable<Embedding> {
    val positions: Int
    val dimensions: Int

    /** Returns the position's numeric embedding; an invalid index throws [IndexOutOfBoundsException]. */
    operator fun get(position: Int): Embedding

    override fun iterator(): Iterator<Embedding>

    fun toList(): List<Embedding>

    /** Source-compatible construction of the default numeric implementation. */
    companion object {
        fun of(vararg embeddings: Embedding): Representation = NumericRepresentation.of(*embeddings)

        fun from(embeddings: List<Embedding>): Representation = NumericRepresentation.from(embeddings)
    }
}
