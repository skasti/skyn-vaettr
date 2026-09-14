package no.skasti.skynvaettr.models

import no.skasti.skynvaettr.representation.Representation

/**
 * Learned or deterministic model component in the processing graph.
 *
 * Models exchange only [Representation] values. Domain semantics such as concrete signal
 * predictions belong to decoders after the model rather than to the model contract itself.
 */
interface Model {
    /** Whether this model can consume the supplied representation. */
    fun supports(input: Representation): Boolean = true

    /** Transform one latent representation into another latent representation. */
    fun forward(input: Representation): Representation
}

/** A [Model] whose parameters/state can be updated from latent training targets. */
interface TrainableModel : Model {
    fun train(
        input: Representation,
        target: Representation,
        weight: Double = 1.0,
    )
}
