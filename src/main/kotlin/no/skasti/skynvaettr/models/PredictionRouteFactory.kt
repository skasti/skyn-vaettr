package no.skasti.skynvaettr.models

import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.Signal

/**
 * One prediction path through a graph-owned model and the decoder that assigns its latent output
 * to a concrete signal.
 */
data class PredictionRoute(
    val model: Model,
    val decoder: PredictionDecoder<*>,
)

/** Creates a prediction route for a newly observed compatible signal. */
fun interface PredictionRouteFactory {
    fun create(sample: Sample<*>): PredictionRoute?
}

/**
 * Default factory for continuous Double-valued signals.
 *
 * Each discovered target gets its own model instance while every model still consumes the complete
 * sensory Representation. This prevents training targets for unrelated signals from being mixed in
 * one scalar output head while still allowing each predictor to learn cross-signal relationships.
 */
class DoublePredictionRouteFactory(
    private val modelFactory: () -> Model = { OnlineKnnModel() },
) : PredictionRouteFactory {
    override fun create(sample: Sample<*>): PredictionRoute? {
        if (sample.value !is Double) return null

        @Suppress("UNCHECKED_CAST")
        val signal = sample.signal as Signal<Double>
        return PredictionRoute(
            model = modelFactory(),
            decoder = NumericPredictionDecoder(signal),
        )
    }
}
