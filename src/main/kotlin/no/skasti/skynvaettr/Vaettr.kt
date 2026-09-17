package no.skasti.skynvaettr

import no.skasti.skynvaettr.environment.Environment
import no.skasti.skynvaettr.runtime.DefaultTopology
import no.skasti.skynvaettr.topology.Topology

/** One running Skynvættr instance. [update] delegates processing to its topology. */
class Vaettr(
    val environment: Environment,
    private val topology: Topology = DefaultTopology(environment),
) {
    fun update() = topology.update()
}
