package no.skasti.skynvaettr

import no.skasti.skynvaettr.environment.Environment
import kotlin.reflect.KClass
import no.skasti.skynvaettr.runtime.EntryPoint
import no.skasti.skynvaettr.runtime.SampleEntryPoint

/**
 * One running Skynvættr instance.
 *
 * [update] pulls newly available values from [environment] and processes them through the configured
 * entrypoints. Each input type is fetched once per update and shared with all matching entrypoints.
 */
class Vaettr(
    val environment: Environment,
    entryPoints: List<EntryPoint<*>> = listOf(SampleEntryPoint()),
) {
    private val entryPoints: List<EntryPoint<*>> = entryPoints.toList()

    fun update() {
        val batches = entryPoints.map { it.inputType }.distinct().associateWith { environment.getNew(it) }
        entryPoints.forEach { entryPoint ->
            if (batches[entryPoint.inputType]?.isNotEmpty() == true) {
                entryPoint.process(batches.getItems(entryPoint.inputType))
            }
        }
    }

    private fun <T: Any> Map<KClass<out Any>, List<Any>>.getItems(inputType: KClass<*>): List<T> {
        val raw = this[inputType] ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        return raw as List<T>
    }
}
