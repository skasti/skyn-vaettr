package no.skasti.skynvaettr.training

import no.skasti.skynvaettr.attention.Attention
import no.skasti.skynvaettr.attention.ScaledDotProductAttention
import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.runtime.SingleSlotPort
import no.skasti.skynvaettr.topology.Node
import no.skasti.skynvaettr.topology.Port

/** Forwards each complete representation to Q, K and V through ordinary synapses. */
class RepresentationFanoutNode(override val name: String = "Representation fanout") : Node {
    val input = SingleSlotPort<Representation>("$name-input")
    val output = SingleSlotPort<Representation>("$name-output")
    override val ports: List<Port<*>> = listOf(input, output)

    init {
        input.onReceive += {
            val next = input.pending
            if (next != null) {
                input.clear()
                output.emit(next)
            }
        }
    }
}

/**
 * Explicit opt-in, general-purpose learnable processing path.
 *
 * The components are ordinary topology nodes; this is wiring, not a new scheduler or an
 * implicit change to DefaultTopology. Pass parameter snapshots/forward passes to an external
 * trainer and route revision-checked replacements back to individual linear nodes.
 *
 * The recorded attention pass and each linear forward pass expose local derivatives; an external\n * trainer can compose them to propagate decoder loss to Q/K/V without adding an optimizer here.\n */
class LearnableProcessingChain(
    queryParameters: LinearParameters,
    keyParameters: LinearParameters,
    valueParameters: LinearParameters,
    decoderParameters: LinearParameters,
    attentionImplementation: Attention = ScaledDotProductAttention(),
) {
    val ingress = RepresentationFanoutNode()
    val query = TrainableLinearNode(queryParameters, "Query")
    val key = TrainableLinearNode(keyParameters, "Key")
    val value = TrainableLinearNode(valueParameters, "Value")
    val attention = RecordedAttentionNode(attentionImplementation)
    val decoder = TrainableLinearNode(decoderParameters, "Decoder")

    val input: Port<Representation> get() = ingress.input
    val output: Port<Representation> get() = decoder.output

    val nodes: List<Node> = listOf(ingress, query, key, value, attention, decoder)

    init {
        require(queryParameters.inputDimensions == keyParameters.inputDimensions &&
            queryParameters.inputDimensions == valueParameters.inputDimensions) {
            "Q/K/V must accept the same representation width"
        }
        require(queryParameters.outputDimensions == keyParameters.outputDimensions) {
            "query and key output dimensions must match"
        }
        require(decoderParameters.inputDimensions == valueParameters.outputDimensions) {
            "decoder input width must match value/attention output width"
        }
        ingress.output.connectTo(query.input, key.input, value.input)
        query.output.connectTo(attention.q)
        key.output.connectTo(attention.k)
        value.output.connectTo(attention.v)
        attention.attention.connectTo(decoder.input)
    }
}
