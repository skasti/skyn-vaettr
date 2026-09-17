package no.skasti.skynvaettr.runtime

import no.skasti.skynvaettr.signals.InMemorySampleStore
import no.skasti.skynvaettr.signals.MutableSampleStore
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.SampleEntryPoint

import java.util.Collections
import java.util.IdentityHashMap
import no.skasti.skynvaettr.environment.Environment
import kotlin.reflect.KClass
import no.skasti.skynvaettr.topology.Node
import no.skasti.skynvaettr.topology.Topology
import no.skasti.skynvaettr.topology.TopologyBuilder
import no.skasti.skynvaettr.topology.Group

/**
 * Initial topology that dispatches new environment values to typed entrypoints.
 *
 * [update] pulls newly available values from [environment] and processes them through the configured
 * entrypoints. Each input type is fetched once per update and shared with all matching entrypoints.
 */
class DefaultTopology(
    private val environment: Environment,
    val sampleStore: MutableSampleStore = InMemorySampleStore(),
    nodes: List<Node> = listOf(SampleEntryPoint(sampleStore)),
    groups: List<Group> = emptyList(),
) : Topology {
    override val nodes: List<Node> = nodes.toList()
    override val groups: List<Group> = groups.toList()

    init {
        val topologyNodes = Collections.newSetFromMap(IdentityHashMap<Node, Boolean>())
        this.nodes.forEach { node ->
            require(topologyNodes.add(node)) { "the same node cannot appear twice in a topology" }
        }
        val groupNames = mutableSetOf<String>()
        val groupedNodes = Collections.newSetFromMap(IdentityHashMap<Node, Boolean>())
        this.groups.forEach { group ->
            require(group.name.isNotBlank()) { "topology group name must not be blank" }
            require(groupNames.add(group.name)) {
                "topology group name '${group.name}' is already registered"
            }
            require(group.nodes.isNotEmpty()) { "topology group '${group.name}' must contain nodes" }
            group.nodes.forEach { node ->
                require(node in topologyNodes) {
                    "topology group '${group.name}' contains a node outside the topology"
                }
                require(groupedNodes.add(node)) {
                    "a node cannot belong to more than one topology group"
                }
            }
        }
    }

    constructor(
        environment: Environment,
        sampleStore: MutableSampleStore = InMemorySampleStore(),
        configure: TopologyBuilder.() -> Unit,
    ) : this(environment, sampleStore, TopologyBuilder().apply(configure).build())

    private constructor(
        environment: Environment,
        sampleStore: MutableSampleStore = InMemorySampleStore(),
        definition: TopologyBuilder.Definition,
    ) : this(environment, sampleStore, definition.nodes, definition.groups)

    override fun update() {
        val entryPoints = entryPoints
        val batches = entryPoints.map { it.inputType }.distinct().associateWith { environment.getNew(it) }
        batches[Sample::class]?.let { rawBatch ->
            @Suppress("UNCHECKED_CAST")
            val samples = rawBatch as List<Sample<*>>
            if (samples.isNotEmpty()) sampleStore.append(samples)
        }
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
