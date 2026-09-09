package no.skasti.skynvaettr.tokenization

data class Token(val value: String) {
    init {
        require(value.isNotBlank()) { "Token value must not be blank" }
    }

    override fun toString(): String = value
}

data class TokenId(val value: Int) {
    init {
        require(value >= 0) { "Token id must be non-negative" }
    }

    override fun toString(): String = value.toString()
}
