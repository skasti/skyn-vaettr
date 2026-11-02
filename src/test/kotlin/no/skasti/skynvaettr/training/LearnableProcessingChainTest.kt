package no.skasti.skynvaettr.training

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import no.skasti.skynvaettr.attention.ScaledDotProductAttention
import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.runtime.SingleSlotPort

class LearnableProcessingChainTest {
    @Test
    fun `linear node records gradients for the actual version used`() {
        val node = TrainableLinearNode(
            LinearParameters(listOf(doubleArrayOf(2.0)), doubleArrayOf(1.0)),
        )
        val output = SingleSlotPort<Representation>("out")
        val passes = SingleSlotPort<LinearForwardPass>("passes")
        node.output.connectTo(output)
        node.forwardPasses.connectTo(passes)

        node.input.receive(Representation.of(Embedding.of(3.0), Embedding.of(4.0)))
        assertEquals(7.0, output.pending!![0][0])
        assertEquals(9.0, output.pending!![1][0])
        val pass = assertNotNull(passes.pending)
        val derivative = pass.backward(Representation.of(Embedding.of(1.0), Embedding.of(-2.0)))
        assertEquals(-5.0, derivative.weights[0][0])
        assertEquals(-1.0, derivative.bias[0])
        assertEquals(2.0, derivative.input[0][0])
        assertEquals(-4.0, derivative.input[1][0])
        assertEquals(0L, pass.revision)

        node.parameterUpdates.receive(LinearParameterUpdate(0, LinearParameters.identity(1)))
        assertEquals(1L, node.revision)
        // Recorded passes are immutable, even after applying replacement parameters.
        assertEquals(2.0, pass.backward(Representation.of(Embedding.of(1.0), Embedding.of(0.0))).input[0][0])
        assertFailsWith<IllegalStateException> {
            node.parameterUpdates.receive(LinearParameterUpdate(0, LinearParameters.identity(1)))
        }
    }

    @Test
    fun `invalid updates preserve revision and pending input`() {
        val node = TrainableLinearNode(LinearParameters.identity(2))
        assertFailsWith<IllegalArgumentException> {
            node.parameterUpdates.receive(LinearParameterUpdate(0, LinearParameters.identity(1)))
        }
        assertEquals(0L, node.revision)
        assertNotNull(node.parameterUpdates.pending)
    }

    @Test
    fun `general chain connects QKV attention to a learnable decoder`() {
        val chain = LearnableProcessingChain(
            queryParameters = LinearParameters.identity(1),
            keyParameters = LinearParameters.identity(1),
            valueParameters = LinearParameters.identity(1),
            decoderParameters = LinearParameters(listOf(doubleArrayOf(2.0)), doubleArrayOf(1.0)),
            attentionImplementation = ScaledDotProductAttention(),
        )
        val observed = SingleSlotPort<Representation>("observed")
        val decoderPass = SingleSlotPort<LinearForwardPass>("decoder-pass")
        chain.output.connectTo(observed)
        chain.decoder.forwardPasses.connectTo(decoderPass)

        chain.input.receive(Representation.of(Embedding.of(3.0)))
        assertEquals(7.0, observed.pending!![0][0])
        assertNotNull(decoderPass.pending)
        assertEquals(6, chain.nodes.size)
        assertNull(chain.decoder.input.pending)
    }

    @Test
    fun `chain rejects inconsistent widths before wiring`() {
        assertFailsWith<IllegalArgumentException> {
            LearnableProcessingChain(
                LinearParameters.identity(2),
                LinearParameters.identity(1),
                LinearParameters.identity(2),
                LinearParameters.identity(2),
            )
        }
    }
}
