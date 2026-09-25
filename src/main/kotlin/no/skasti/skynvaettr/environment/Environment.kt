package no.skasti.skynvaettr.environment

import kotlin.reflect.KClass

/**
 * One batch of values made available to a processing topology.
 *
 * The sequence identifies the batch for replay. The values are grouped by the requested input
 * type; a value matching several requested types may therefore occur in several map entries.
 */
data class ProcessingInput(
    val sequence: ULong,
    val values: Map<KClass<*>, List<Any>>,
)

/**
 * Provides newly available values from an external environment.
 *
 * A call to [getNew] receives all input types needed by a topology and returns one processing
 * batch. A value matching multiple requested types is included in every matching map entry.
 * Implementations may retain the returned batches so that [getHistory] can replay them.
 */
interface Environment {
    /**
     * Returns the next non-empty batch grouped by the requested input type, or null when there is
     * no new input.
     *
     * A value that matches more than one requested type is included in every matching map entry.
     */
    fun getNew(types: List<KClass<*>>): ProcessingInput?

    /**
     * Returns previously delivered processing batches in the half-open sequence interval
     * [from, to). The returned values are projected to the requested input types.
     */
    fun getHistory(
        types: List<KClass<*>>,
        from: ULong,
        to: ULong,
    ): List<ProcessingInput>

    /** Type-safe convenience form for requesting one input type. */
    fun <T : Any> getNew(type: KClass<T>): List<T> {
        val batch = getNew(listOf(type))?.values?.get(type).orEmpty()
        return batch.map { value -> type.javaObjectType.cast(value) }
    }
}

/** Type-safe convenience form of [Environment.getNew]. */
inline fun <reified T : Any> Environment.getNew(): List<T> = getNew(T::class)
