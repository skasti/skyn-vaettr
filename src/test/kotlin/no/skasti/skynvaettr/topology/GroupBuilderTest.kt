package no.skasti.skynvaettr.topology

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import no.skasti.skynvaettr.runtime.SingleSlotPort

class GroupBuilderTest {
    @Test
    fun `build collects nodes and exposes their ports by name`() {
        val group = Group.build("Memory system") {
            val inputNode = add(TestNode("input"))
            val outputNode = add(TestNode("output"))

            expose("input", inputNode.input)
            inputNode.output.connectTo(outputNode.input)
            expose("output", outputNode.output)
        }

        assertEquals("Memory system", group.name)
        assertEquals(2, group.nodes.size)
        assertSame(group.nodes[0].ports[0], group.port<Int>("input"))
        val output: Port<Int> = group["output"]
        assertSame(group.nodes[1].ports[1], output)

        val source = TestNode("source")
        source.output.connectTo(group["input"])
    }

    @Test
    fun `expose rejects ports outside the group`() {
        val external = TestNode("external")

        assertFailsWith<IllegalArgumentException> {
            Group.build {
                expose("input", external.input)
            }
        }
    }

    @Test
    fun `expose rejects duplicate names`() {
        assertFailsWith<IllegalArgumentException> {
            Group.build {
                val node = add(TestNode("node"))
                expose("input", node.input)
                expose("input", node.output)
            }
        }
    }

    private class TestNode(label: String) : Node {
        val input = SingleSlotPort<Int>("$label-input")
        val output = SingleSlotPort<Int>("$label-output")
        override val ports: List<Port<*>> = listOf(input, output)
    }
}
