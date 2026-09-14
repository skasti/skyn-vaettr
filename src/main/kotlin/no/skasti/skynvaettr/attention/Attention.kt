package no.skasti.skynvaettr.attention

import no.skasti.skynvaettr.representation.Representation

/** Replaceable attention mechanism operating on generic latent representations. */
interface Attention {
    fun apply(
        queries: Representation,
        keys: Representation,
        values: Representation,
    ): AttentionResult

    fun selfAttention(inputs: Representation): AttentionResult = apply(inputs, inputs, inputs)
}
