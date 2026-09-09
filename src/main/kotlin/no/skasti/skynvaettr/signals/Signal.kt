package no.skasti.skynvaettr.signals

/**
 * Defines one uniquely identified value that a Skynvættr can receive from its environment.
 *
 * A Signal describes what can be sampled. The values received over time are represented
 * by [Sample] instances.
 */
class Signal<T>(
    val id: SignalId,
    metadata: Map<String, Any> = emptyMap(),
) {
    val metadata: Map<String, Any> = metadata.toMap()

    /**
     * Human-readable view of the signal identity.
     */
    val name: String
        get() = id.value

    override fun equals(other: Any?): Boolean =
        other is Signal<*> && id == other.id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "Signal(id=$id)"
}
