package no.skasti.skynvaettr.environment

import kotlin.reflect.KClass

/**
 * Provides newly available values from an external environment.
 *
 * A call to [getNew] receives all input types needed by a topology and returns one batch per type.
 * Each type has its own consumption position. A value matching multiple requested types is included
 * in every matching batch.
 */
interface Environment {
    /**
     * Returns newly available values grouped by the requested input type.
     *
     * A value that matches more than one requested type is included in each matching batch. The
     * returned map may omit types with no new values; callers should treat those as empty batches.
     */
    fun getNew(types: List<KClass<*>>): Map<KClass<*>, List<Any>>

    /** Type-safe convenience form for requesting one input type. */
    fun <T : Any> getNew(type: KClass<T>): List<T> {
        val batch = getNew(listOf(type))[type].orEmpty()
        return batch.map { value -> type.javaObjectType.cast(value) }
    }
}

/** Type-safe convenience form of [Environment.getNew]. */
inline fun <reified T : Any> Environment.getNew(): List<T> = getNew(T::class)
