package no.skasti.skynvaettr.signals

/**
 * Stable identity of a [Signal].
 */
@JvmInline
value class SignalId(val value: String) {
    init {
        require(value.isNotBlank()) { "Signal id must not be blank" }
    }

    override fun toString(): String = value
}
