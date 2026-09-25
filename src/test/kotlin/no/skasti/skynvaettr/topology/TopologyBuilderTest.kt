package no.skasti.skynvaettr.topology

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class TopologyBuilderTest {
    @Test
    fun `add flattens group nodes and retains the group by name`() {
        val builder = TopologyBuilder()
        val node = TestNode("input")
        val group = TestGroup("signals", node)

        builder.add(group)
        val definition = builder.build()

        assertEquals(listOf(node), definition.nodes)
        assertSame(group, definition.groups["signals"])
    }

    @Test
    fun `add accepts standalone nodes`() {
        val builder = TopologyBuilder()
        val node = TestNode("input")

        builder.add(node)

        assertEquals(listOf(node), builder.build().nodes)
        assertEquals(emptyMap(), builder.build().groups)
    }

    @Test
    fun `add rejects duplicate group names`() {
        val builder = TopologyBuilder()
        val first = TestGroup("signals", TestNode("first"))
        val second = TestGroup("signals", TestNode("second"))

        builder.add(first)

        assertFailsWith<IllegalArgumentException> {
            builder.add(second)
        }
    }

    private class TestNode(
        private val label: String,
    ) : Node {
        override val name: String = label
        override val ports: List<Port<*>> = emptyList()
    }

    private class TestGroup(
        override val name: String,
        node: Node,
    ) : Group {
        override val nodes: List<Node> = listOf(node)
    }
}
