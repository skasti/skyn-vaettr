package no.skasti.skynvaettr.attention

import no.skasti.skynvaettr.runtime.SingleSlotPort

import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.topology.Node
import no.skasti.skynvaettr.topology.Port

/**
 * Applies [implementation] once all three inputs have received a representation.
 * Inputs are retained if computation fails, and cleared before the output is delivered.
 * Each computation consumes one value from each input; values are not reused across rounds.
 */
class AttentionNode(
    private val implementation: Attention,
    override val name: String = "Attention",
) : Node {
    val q = SingleSlotPort<Representation>("q")
    val k = SingleSlotPort<Representation>("k")
    val v = SingleSlotPort<Representation>("v")
    val attention: Port<Representation> = SingleSlotPort("attention")

    override val ports: List<Port<*>> = listOf(q, k, v, attention)

    init {
        listOf(q, k, v).forEach { port ->
            port.onReceive += { processIfReady() }
        }
    }

    private fun processIfReady() {
        val queries = q.pending ?: return
        val keys = k.pending ?: return
        val values = v.pending ?: return
        val result = implementation.apply(queries, keys, values)
        q.clear()
        k.clear()
        v.clear()
        attention.emit(result.output)
    }
}
