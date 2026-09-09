package no.skasti.skynvaettr.signals

/**
 * Defines one uniquely named value that a Skynvættr can receive from its environment.
 *
 * A Signal describes what can be sampled. The values received over time are represented
 * by [Sample] instances.
 */
class Signal<T>(
    val name: String,
    metadata: Map<String, Any> = emptyMap(),
) {
    val metadata: Map<String, Any> = metadata.toMap()

    init {
        require(name.isNotBlank()) { "Signal name must not be blank" }
    }

    override fun equals(other: Any?): Boolean =
        other is Signal<*> && name == other.name

    override fun hashCode(): Int = name.hashCode()

    override fun toString(): String = "Signal(name=$name)"
}
