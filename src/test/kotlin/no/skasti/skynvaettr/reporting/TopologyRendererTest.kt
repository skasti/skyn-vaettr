package no.skasti.skynvaettr.reporting

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import no.skasti.skynvaettr.runtime.SingleSlotPort
import no.skasti.skynvaettr.topology.Node
import no.skasti.skynvaettr.topology.Topology

class TopologyRendererTest {
    @Test
    fun `draws distinct nodes and directed connections through their ports`() {
        val first = TestNode()
        val second = TestNode()
        val isolated = TestNode()
        first.port.connectTo(second.port)
        first.port.connectTo(second.port)
        second.port.connectTo(first.port)
        second.port.connectTo(second.port)

        val dot = TopologyRenderer().toDot(topology(first, second, isolated))

        assertEquals(3, Regex("label=").findAll(dot).count())
        assertEquals(3, Regex(" -> ").findAll(dot).count())
        assertTrue("n0 -> n1;" in dot)
        assertTrue("n1 -> n0;" in dot)
        assertTrue("n1 -> n1;" in dot)
        assertTrue("n2 [label=\"TestNode\"];" in dot)
    }

    @Test
    fun `rejects a connection to a node omitted from the topology`() {
        val source = TestNode()
        source.port.connectTo(TestNode().port)

        assertFailsWith<IllegalArgumentException> {
            TopologyRenderer().toDot(topology(source))
        }
    }

    private fun topology(vararg nodes: Node): Topology = object : Topology {
        override val nodes = nodes.toList()
        override fun update() = Unit
    }

    private class TestNode : Node {
        val port = SingleSlotPort<Int>("port")
        override val ports = listOf(port)
    }
}
