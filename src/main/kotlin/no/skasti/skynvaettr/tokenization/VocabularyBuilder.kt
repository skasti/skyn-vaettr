package no.skasti.skynvaettr.tokenization

class VocabularyBuilder(
    private val reservedTokens: List<Token> = ReservedTokens.default,
    private val minimumFrequency: Int = 1,
) {
    init {
        require(minimumFrequency > 0) { "Minimum frequency must be positive" }
        require(reservedTokens.distinct().size == reservedTokens.size) {
            "Reserved tokens must be unique"
        }
        require(ReservedTokens.UNK in reservedTokens) {
            "Reserved tokens must include ${ReservedTokens.UNK}"
        }
    }

    private val frequencies = linkedMapOf<Token, Int>()

    fun add(tokens: Iterable<Token>): VocabularyBuilder = apply {
        tokens.forEach { token ->
            if (token !in reservedTokens) {
                frequencies[token] = frequencies.getOrDefault(token, 0) + 1
            }
        }
    }

    fun build(): FixedVocabulary {
        val learned =
            frequencies.entries
                .filter { it.value >= minimumFrequency }
                .sortedWith(
                    compareByDescending<Map.Entry<Token, Int>> { it.value }
                        .thenBy { it.key.value },
                )
                .map { it.key }

        return FixedVocabulary(
            tokens = reservedTokens + learned,
            unknownToken = ReservedTokens.UNK,
        )
    }
}
