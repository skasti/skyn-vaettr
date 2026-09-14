package no.skasti.skynvaettr.representation

/**
 * Generic two-dimensional latent representation exchanged between model components.
 *
 * A representation is a sequence of positions, each carrying one fixed-width [Embedding]. Core does
 * not assign semantics to either axis: positions may represent observations, tokens, time steps,
 * memory slots, or another model-specific structure.
 */
class Representation private constructor(
    embeddings: List<Embedding>,
) : Iterable<Embedding> {
    private val embeddings: List<Embedding> = embeddings.toList()

    val positions: Int
        get() = embeddings.size

    val dimensions: Int = embeddings.first().dimensions

    init {
        require(embeddings.isNotEmpty()) { "representation must contain at least one position" }
        require(embeddings.all { it.dimensions == dimensions }) {
            "all embeddings in a representation must have the same dimensions"
        }
    }

    operator fun get(position: Int): Embedding = embeddings[position]

    override fun iterator(): Iterator<Embedding> = embeddings.iterator()

    fun toList(): List<Embedding> = embeddings.toList()

    companion object {
        fun of(vararg embeddings: Embedding): Representation = from(embeddings.asList())

        fun from(embeddings: List<Embedding>): Representation = Representation(embeddings)
    }
}
