package no.skasti.skynvaettr

import kotlin.test.Test
import kotlin.test.assertEquals
import no.skasti.skynvaettr.environment.InMemoryEnvironment
import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.runtime.EntryPoint
import no.skasti.skynvaettr.runtime.Port
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
