package no.skasti.skynvaettr.runtime

import no.skasti.skynvaettr.signals.SampleEntryPoint

import no.skasti.skynvaettr.environment.Environment
import kotlin.reflect.KClass
import no.skasti.skynvaettr.topology.Node
import no.skasti.skynvaettr.topology.Topology

/**
 * Initial topology that dispatches new environment values to typed entrypoints.
 *
 * [update] pulls newly available values from [environment] and processes them through the configured
 * entrypoints. Each input type is fetched once per update and shared with all matching entrypoints.
 */
class DefaultTopology(
    private val environment: Environment,
    nodes: List<Node> = listOf(SampleEntryPoint()),
) : Topology {
    override val nodes: List<Node> = nodes.toList()

    override fun update() {
        val entryPoints = entryPoints
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
