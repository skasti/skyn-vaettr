package no.skasti.skynvaettr.representation

/**
 * Default immutable numeric [Representation].
 *
 * Copies the input list and returns independent lists from [toList]. Its [Embedding] rows are
 * immutable and may be shared. A representation has at least one position, with the same
 * dimensions for every embedding. Equality is based on reference identity.
 */
class NumericRepresentation private constructor(
    embeddings: List<Embedding>,
) : Representation {
    private val embeddings: List<Embedding> = embeddings.toList()

    init {
        require(this.embeddings.isNotEmpty()) { "representation must contain at least one position" }
    }

    override val positions: Int
        get() = embeddings.size

    override val dimensions: Int = this.embeddings.first().dimensions

    init {
        require(this.embeddings.all { it.dimensions == dimensions }) {
            "all embeddings in a representation must have the same dimensions"
        }
    }

    override operator fun get(position: Int): Embedding = embeddings[position]

    override fun iterator(): Iterator<Embedding> = embeddings.iterator()

    override fun toList(): List<Embedding> = embeddings.toList()

    companion object {
        fun of(vararg embeddings: Embedding): NumericRepresentation = from(embeddings.asList())

        fun from(embeddings: List<Embedding>): NumericRepresentation = NumericRepresentation(embeddings)
    }
}
