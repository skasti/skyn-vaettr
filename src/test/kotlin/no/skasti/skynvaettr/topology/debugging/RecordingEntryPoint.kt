package no.skasti.skynvaettr.topology.debugging

import kotlin.reflect.KClass
import no.skasti.skynvaettr.topology.EntryPoint
import no.skasti.skynvaettr.topology.Port

/**
 * An entrypoint that keeps every batch it receives for assertions in tests.
 *
 * [ports] and [onProcess] are optional so a test can also use the recorder as a source for a
 * connected topology without having to define another entrypoint implementation.
 */
class RecordingEntryPoint<T : Any>(
    override val name: String,
    override val inputType: KClass<T>,
    override val ports: List<Port<*>> = emptyList(),
    private val onProcess: RecordingEntryPoint<T>.(List<T>) -> Unit = {},
) : EntryPoint<T> {
    /** All batches passed to [process], in dispatch order. */
    val received = mutableListOf<List<T>>()

    override fun process(items: List<T>) {
        received += items.toList()
        onProcess(items)
    }
}
