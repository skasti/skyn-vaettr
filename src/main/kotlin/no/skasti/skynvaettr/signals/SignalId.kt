package no.skasti.skynvaettr.signals

/**
 * Stable identity of a [Signal].
 */
data class SignalId(val value: String) {
    init {
        require(value.isNotBlank()) { "Signal id must not be blank" }
    }

    override fun toString(): String = value
}
