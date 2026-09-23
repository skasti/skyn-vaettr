package no.skasti.skynvaettr.runtime

import no.skasti.skynvaettr.signals.InMemorySampleStore
import no.skasti.skynvaettr.signals.SampleEntryPoint
import no.skasti.skynvaettr.signals.SampleStore

import java.util.Collections
import java.util.IdentityHashMap
import no.skasti.skynvaettr.environment.Environment
import no.skasti.skynvaettr.environment.ProcessingInput
import kotlin.reflect.KClass
import no.skasti.skynvaettr.topology.EntryPoint
import no.skasti.skynvaettr.topology.Node
import no.skasti.skynvaettr.topology.Topology
import no.skasti.skynvaettr.topology.TopologyBuilder
import no.skasti.skynvaettr.topology.Group

/**
 * Initial topology that dispatches new environment values to typed entrypoints.
 *
 * [update] pulls newly available values from [environment] and processes them through the configured
 * entrypoints. All distinct input types are fetched together once per update and shared with all
 * matching entrypoints. The environment assigns the returned [ProcessingInput] its replay sequence.
 * Historical samples are provided by the environment or the explicitly supplied [sampleStore].
 * A value matching multiple entrypoint types is delivered in each corresponding batch.
 */
class DefaultTopology(
    private val environment: Environment,
    sampleStore: SampleStore? = null,
    nodes: List<Node> = listOf(SampleEntryPoint(resolveDefaultSampleStore(environment, sampleStore))),
    groups: List<Group> = emptyList(),
) : Topology {
    val sampleStore: SampleStore
    override val nodes: List<Node>
    override val groups: Map<String, Group> = groups.associateBy { it.name }

    init {
        val environmentSampleStore = environment as? SampleStore
        this.sampleStore = sampleStore ?: environmentSampleStore ?: error(
            "the default sample entrypoint requires an Environment that implements SampleStore " +
                "or an explicit sample store",
        )
        this.nodes = nodes.toList()

        this.nodes.filterIsInstance<SampleEntryPoint>().forEach { entryPoint ->
            require(entryPoint.sampleStore === this.sampleStore) {
                "SampleEntrypoint must use the topology sample store"
            }
        }

        val topologyNodes = Collections.newSetFromMap(IdentityHashMap<Node, Boolean>())
        this.nodes.forEach { node ->
            require(topologyNodes.add(node)) { "the same node cannot appear twice in a topology" }
        }
        this.nodes.filterIsInstance<EntryPoint<*>>().forEach { entryPoint ->
            require(entryPoint.inputType != Any::class) {
                "entrypoint '${entryPoint.name}' must declare a specific input type, not Any"
            }
        }

        val groupedNodes = Collections.newSetFromMap(IdentityHashMap<Node, Boolean>())
        this.groups.forEach { (groupName, group) ->
            require(groupName.isNotBlank()) { "topology group name must not be blank" }
            require(group.nodes.isNotEmpty()) { "topology group '${groupName}' must contain nodes" }
            group.nodes.forEach { node ->
                require(node in topologyNodes) {
                    "topology group '${groupName}' contains a node outside the topology"
                }
                require(groupedNodes.add(node)) {
                    "a node cannot belong to more than one topology group"
                }
            }
        }
    }

    constructor(
        environment: Environment,
        sampleStore: SampleStore? = null,
        configure: TopologyBuilder.() -> Unit,
    ) : this(environment, sampleStore, TopologyBuilder().apply(configure).build())

    private constructor(
        environment: Environment,
        sampleStore: SampleStore? = null,
        definition: TopologyBuilder.Definition,
    ) : this(environment, sampleStore, definition.nodes, definition.groups)

    override fun update() {
        val entryPoints = entryPoints
        val inputTypes = entryPoints.map { it.inputType }.distinct()
        val input = environment.getNew(inputTypes) ?: return
        val batches = input.values
        entryPoints.forEach { entryPoint ->
            if (batches[entryPoint.inputType]?.isNotEmpty() == true) {
                entryPoint.process(batches.getItems(entryPoint.inputType))
            }
        }
    }

    private companion object {
        fun resolveDefaultSampleStore(
            environment: Environment,
            sampleStore: SampleStore?,
        ): SampleStore = sampleStore ?: (environment as? SampleStore) ?: error(
            "the default sample entrypoint requires an Environment that implements SampleStore " +
                "or an explicit sample store",
        )
    }

    private fun <T: Any> Map<KClass<*>, List<Any>>.getItems(inputType: KClass<*>): List<T> {
        val raw = this[inputType] ?: return emptyList()
        @Suppress("UNCHECKED_CAST")
        return raw as List<T>
    }
}
