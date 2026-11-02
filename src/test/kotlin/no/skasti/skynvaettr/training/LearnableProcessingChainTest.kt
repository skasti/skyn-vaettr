package no.skasti.skynvaettr.training

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import no.skasti.skynvaettr.attention.LinearRepresentationProjection
import no.skasti.skynvaettr.attention.ProjectionNode
import no.skasti.skynvaettr.attention.ScaledDotProductAttention
import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.runtime.SingleSlotPort

class LearnableProcessingChainTest {

    @Test
    fun `trainable node specializes projection node without changing ordinary ports`() {
        val parameters = LinearParameters(listOf(doubleArrayOf(2.0)), doubleArrayOf(1.0))
        val staticNode = ProjectionNode("linear", LinearRepresentationProjection(parameters))
        val learnedNode = TrainableProjectionNode(LinearRepresentationProjection(parameters))
        val input = Representation.of(Embedding.of(3.0))
        val staticOutput = SingleSlotPort<Representation>("static")
        val learnedOutput = SingleSlotPort<Representation>("learned")
        staticNode.output.connectTo(staticOutput)
        learnedNode.output.connectTo(learnedOutput)
        staticNode.input.receive(input)
        learnedNode.input.receive(input)

        kotlin.test.assertIs<ProjectionNode>(learnedNode)
        assertEquals(staticOutput.pending!!.toList(), learnedOutput.pending!!.toList())
        assertEquals(2, staticNode.ports.size)
        assertEquals(6, learnedNode.ports.size)
        assertEquals(7.0, learnedOutput.pending!![0][0])
    }

    @Test
    fun `linear node records gradients for the actual version used`() {
        val node = TrainableProjectionNode(
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
        val node = TrainableProjectionNode(LinearParameters.identity(2))
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

    @Test
    fun `prediction gradient propagates through attention to Q K and V`() {
        val chain = LearnableProcessingChain(
            LinearParameters.identity(1), LinearParameters.identity(1),
            LinearParameters.identity(1), LinearParameters.identity(1),
        )
        val queryPass = SingleSlotPort<LinearForwardPass>("query-pass")
        val keyPass = SingleSlotPort<LinearForwardPass>("key-pass")
        val valuePass = SingleSlotPort<LinearForwardPass>("value-pass")
        val attentionPass = SingleSlotPort<AttentionForwardPass>("attention-pass")
        val decoderPass = SingleSlotPort<LinearForwardPass>("decoder-pass")
        chain.query.forwardPasses.connectTo(queryPass)
        chain.key.forwardPasses.connectTo(keyPass)
        chain.value.forwardPasses.connectTo(valuePass)
        chain.attention.forwardPasses.connectTo(attentionPass)
        chain.decoder.forwardPasses.connectTo(decoderPass)

        chain.input.receive(Representation.of(Embedding.of(1.0), Embedding.of(2.0)))
        val decoderGradient = assertNotNull(decoderPass.pending).backward(
            Representation.of(Embedding.of(1.0), Embedding.of(0.0)),
        )
        val attentionGradients = assertNotNull(attentionPass.pending).backward(decoderGradient.input)
        val q = assertNotNull(queryPass.pending).backward(attentionGradients.query)
        val k = assertNotNull(keyPass.pending).backward(attentionGradients.key)
        val v = assertNotNull(valuePass.pending).backward(attentionGradients.value)
        assertEquals(1, q.weights.size)
        assertEquals(1, k.weights.size)
        assertEquals(1, v.weights.size)
        // Nontrivial two-position attention gives Q/K gradients, not only value gradients.
        kotlin.test.assertTrue(kotlin.math.abs(q.weights[0][0]) > 1e-8)
        kotlin.test.assertTrue(kotlin.math.abs(k.weights[0][0]) > 1e-8)
        kotlin.test.assertTrue(kotlin.math.abs(v.weights[0][0]) > 1e-8)
    }

    @Test
    fun `attention backward agrees with centered finite differences`() {
        val attention = ScaledDotProductAttention()
        val q = Representation.of(Embedding.of(0.4), Embedding.of(-0.8))
        val k = Representation.of(Embedding.of(1.2), Embedding.of(-0.3))
        val v = Representation.of(Embedding.of(2.0), Embedding.of(-1.0))
        val result = attention.apply(q, k, v)
        val pass = AttentionForwardPass(q, k, v, result.output, result.weights)
        val seed = Representation.of(Embedding.of(1.0), Embedding.of(-0.4))
        val grads = pass.backward(seed)
        val epsilon = 1e-6

        fun loss(qq: Representation, kk: Representation, vv: Representation): Double {
            val out = attention.apply(qq, kk, vv).output
            return (0 until out.positions).sumOf { i -> out[i][0] * seed[i][0] }
        }
        fun changed(original: Representation, position: Int, delta: Double): Representation =
            Representation.from(original.mapIndexed { index, e ->
                Embedding.of(e[0] + if (index == position) delta else 0.0)
            })

        for (position in 0 until 2) {
            val qNumeric = (loss(changed(q, position, epsilon), k, v) -
                loss(changed(q, position, -epsilon), k, v)) / (2 * epsilon)
            val kNumeric = (loss(q, changed(k, position, epsilon), v) -
                loss(q, changed(k, position, -epsilon), v)) / (2 * epsilon)
            val vNumeric = (loss(q, k, changed(v, position, epsilon)) -
                loss(q, k, changed(v, position, -epsilon))) / (2 * epsilon)
            kotlin.test.assertEquals(qNumeric, grads.query[position][0], 1e-6)
            kotlin.test.assertEquals(kNumeric, grads.key[position][0], 1e-6)
            kotlin.test.assertEquals(vNumeric, grads.value[position][0], 1e-6)
        }
    }
}
