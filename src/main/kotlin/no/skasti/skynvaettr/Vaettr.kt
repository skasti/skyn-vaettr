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

    fun update(): List<Representation> =
        entryPoints.mapNotNull { entryPoint -> processNew(entryPoint) }

    private fun <T : Any> processNew(entryPoint: EntryPoint<T>): Representation? {
        val items = environment.getNew(entryPoint.inputType)
        return if (items.isEmpty()) null else entryPoint.process(items)
    }
}
