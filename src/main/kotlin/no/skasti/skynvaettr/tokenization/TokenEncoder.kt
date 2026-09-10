package no.skasti.skynvaettr.tokenization

/**
 * Maps one lexical [Token] to one fixed-width numeric representation.
 *
 * Tokenization and representation are intentionally separate concerns: a tokenizer decides which
 * lexical units a signal id contains, while an encoder decides how each lexical unit is represented
 * numerically. Implementations may be deterministic or learned and may support tokens that were not
 * present when the encoder was created.
 */
interface TokenEncoder {
    /** Number of components returned by [encode]. */
    val dimensions: Int

    /**
     * Encodes [token] as a vector of exactly [dimensions] components.
     *
     * The returned values remain valid after this call and must not subsequently be mutated by the
     * encoder. Implementations are free to choose their allocation and caching strategy.
     */
    fun encode(token: Token): DoubleArray
}
