package no.skasti.skynvaettr

import kotlin.test.Test
import kotlin.test.assertEquals
import no.skasti.skynvaettr.environment.InMemoryEnvironment
import no.skasti.skynvaettr.topology.Node
import no.skasti.skynvaettr.topology.Topology

class VaettrTest {
    @Test
    fun `each update delegates to the configured topology`() {
        var updates = 0
        val topology = object : Topology {
            override val nodes: List<Node> = emptyList()

            override fun update() {
                updates++
            }
        }
        val vaettr = Vaettr(InMemoryEnvironment(), topology)

        vaettr.update()
        assertEquals(1, updates)
        vaettr.update()
        assertEquals(2, updates)
    }
}
