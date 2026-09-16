package no.skasti.skynvaettr.environment

import kotlin.reflect.KClass

/**
 * Provides newly available values from an external environment.
 *
 * Each supported type has its own consumption position. Calling [getNew] returns values of that
 * type added since the previous call for the same type.
 */
interface Environment {
    fun <T : Any> getNew(type: KClass<T>): List<T>
}

/** Type-safe convenience form of [Environment.getNew]. */
inline fun <reified T : Any> Environment.getNew(): List<T> = getNew(T::class)
