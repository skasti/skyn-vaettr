package no.skasti.skynvaettr.tokenization

interface Vocabulary {
    val size: Int
    val tokens: List<Token>

    fun idOf(token: Token): TokenId
    fun tokenOf(id: TokenId): Token
}

class FixedVocabulary internal constructor(
    override val tokens: List<Token>,
    unknownToken: Token,
) : Vocabulary {
    private val idsByToken = tokens.withIndex().associate { (index, token) -> token to TokenId(index) }
    private val unknownId = requireNotNull(idsByToken[unknownToken]) {
        "Unknown token $unknownToken must be present in the vocabulary"
    }

    override val size: Int
        get() = tokens.size

    override fun idOf(token: Token): TokenId = idsByToken[token] ?: unknownId

    override fun tokenOf(id: TokenId): Token =
        tokens.getOrNull(id.value)
            ?: throw IndexOutOfBoundsException(
                "Token id ${id.value} is outside vocabulary of size $size",
            )
}

object ReservedTokens {
    val PAD = Token("<PAD>")
    val UNK = Token("<UNK>")
    val MASK = Token("<MASK>")

    val default: List<Token> = listOf(PAD, UNK, MASK)
}
