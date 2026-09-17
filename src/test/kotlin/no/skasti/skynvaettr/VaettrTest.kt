package no.skasti.skynvaettr

import kotlin.test.Test
import kotlin.test.assertEquals
import no.skasti.skynvaettr.environment.InMemoryEnvironment
import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.topology.EntryPoint
import no.skasti.skynvaettr.topology.Port
import no.skasti.skynvaettr.runtime.SingleSlotPort
import org.junit.jupiter.api.assertDoesNotThrow

class VaettrTest {
    @Test
    fun `update processes only new values for each typed entrypoint`() {
        val environment = InMemoryEnvironment()
        val entryPoint = RecordingEntryPoint()
        val vaettr = Vaettr(environment, listOf(entryPoint))

        environment.append(listOf(1, "ignored", 2))

        assertDoesNotThrow { vaettr.update() }
        assertEquals(listOf(listOf(1, 2)), entryPoint.received)

        environment.append(3)

        assertDoesNotThrow { vaettr.update() }
        assertEquals(listOf(listOf(1, 2), listOf(3)), entryPoint.received)
    }

    @Test
    fun `multiple entrypoints receive their own typed values`() {
        val environment = InMemoryEnvironment()
        val integers = RecordingEntryPoint()
        val strings = StringEntryPoint()
        val vaettr = Vaettr(environment, listOf(integers, strings))

        environment.append(listOf(1, "one", 2, "two"))

        assertDoesNotThrow { vaettr.update() }
        assertEquals(listOf(listOf(1, 2)), integers.received)
        assertEquals(listOf(listOf("one", "two")), strings.received)
    }

    @Test
    fun `entrypoints sharing an input type receive every new batch`() {
        val environment = InMemoryEnvironment()
        val first = RecordingEntryPoint()
        val second = RecordingEntryPoint()
        val strings = StringEntryPoint()
        val vaettr = Vaettr(environment, listOf(first, strings, second))

        environment.append(listOf(1, "one", 2))
        vaettr.update()

        assertEquals(listOf(listOf(1, 2)), first.received)
        assertEquals(first.received, second.received)
        assertEquals(listOf(listOf("one")), strings.received)

        vaettr.update()

        assertEquals(listOf(listOf(1, 2)), first.received)
        assertEquals(first.received, second.received)
        assertEquals(listOf(listOf("one")), strings.received)

        environment.append(3)
        vaettr.update()

        assertEquals(listOf(listOf(1, 2), listOf(3)), first.received)
        assertEquals(first.received, second.received)
        assertEquals(listOf(listOf("one")), strings.received)
    }

    private class RecordingEntryPoint : EntryPoint<Int> {
        override val inputType = Int::class
        val received = mutableListOf<List<Int>>()
        val output = SingleSlotPort<Representation>("output")
        override val ports: List<Port<*>>
            get() = listOf(output)

        override fun process(items: List<Int>) {
            received += items
            output.emit(Representation.of(Embedding.of(items.first().toDouble())))
        }
    }

    private class StringEntryPoint : EntryPoint<String> {
        override val inputType = String::class
        val received = mutableListOf<List<String>>()
        val output = SingleSlotPort<Representation>("output")
        override val ports: List<Port<*>>
            get() = listOf(output)

        override fun process(items: List<String>) {
            received += items
            output.emit(Representation.of(Embedding.of(items.first().length.toDouble())))
        }
    }
}
