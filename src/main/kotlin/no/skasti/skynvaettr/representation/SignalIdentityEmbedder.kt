package no.skasti.skynvaettr.representation

import no.skasti.skynvaettr.signals.Signal
import no.skasti.skynvaettr.signals.SignalId
import no.skasti.skynvaettr.tokenization.DelimitedSignalTokenizer
import no.skasti.skynvaettr.tokenization.DeterministicByteTokenEncoder
import no.skasti.skynvaettr.tokenization.SignalTokenizer
import no.skasti.skynvaettr.tokenization.TokenEncoder

/**
 * Source-agnostic baseline that turns a signal identity into one fixed-width embedding.
 *
 * Signal ids are tokenized first. Each lexical token is then represented independently by the
 * configured [TokenEncoder], and the token vectors are mean pooled. This preserves the existing
 * separation between tokenization and numeric token representation while providing a convenient
 * signal-level input to later model components.
 *
 * The default encoder is deterministic and therefore this class must not be interpreted as a
 * learned semantic embedding. Learned embedders can implement [Embedder] without changing callers.
 */
class SignalIdentityEmbedder(
    private val tokenizer: SignalTokenizer = DelimitedSignalTokenizer(),
    private val tokenEncoder: TokenEncoder = DeterministicByteTokenEncoder(),
) : Embedder<SignalId> {
    override val dimensions: Int = tokenEncoder.dimensions

    init {
        require(dimensions > 0) { "token encoder dimensions must be positive" }
    }

    override fun embed(value: SignalId): Embedding {
        val tokens = tokenizer.tokenize(value)
        require(tokens.isNotEmpty()) { "Signal $value produced no lexical tokens" }

        val vectors = tokens.map(tokenEncoder::encode)
        vectors.forEach { vector ->
            require(vector.size == dimensions) {
                "Token encoder returned ${vector.size} dimensions, expected $dimensions"
            }
            require(vector.all(Double::isFinite)) { "token embedding components must be finite" }
        }

        return Embedding.from(
            DoubleArray(dimensions) { dimension ->
                vectors.sumOf { it[dimension] } / vectors.size
            },
        )
    }

    fun embed(signal: Signal<*>): Embedding = embed(signal.id)
}
