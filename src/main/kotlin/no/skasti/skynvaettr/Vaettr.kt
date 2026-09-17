package no.skasti.skynvaettr

import no.skasti.skynvaettr.environment.Environment
import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.runtime.EntryPoint
import no.skasti.skynvaettr.runtime.SampleEntryPoint

/**
 * One running Skynvættr instance.
 *
 * [update] pulls newly available values from [environment] and processes them through the configured
 * entrypoints. Each entrypoint consumes only values of its declared input type.
 */
class Vaettr(
    val environment: Environment,
    entryPoints: List<EntryPoint<*>> = listOf(SampleEntryPoint()),
) {
    private val entryPoints: List<EntryPoint<*>> = entryPoints.toList()

    fun update() = entryPoints.forEach { entryPoint -> processNew(entryPoint) }

    private fun <T : Any> processNew(entryPoint: EntryPoint<T>) {
        val items = environment.getNew(entryPoint.inputType)
        if (items.isNotEmpty()) {
            entryPoint.process(items)
        }
    }
}
