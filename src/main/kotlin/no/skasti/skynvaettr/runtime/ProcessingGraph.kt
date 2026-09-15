package no.skasti.skynvaettr.runtime

import no.skasti.skynvaettr.expectations.Prediction
import no.skasti.skynvaettr.models.Model
import no.skasti.skynvaettr.models.PredictionDecoder
import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.signals.Sample

/** One model invocation completed by the graph for the current sense cycle. */
data class PredictionExecution(
    val model: Model,
    val decoder: PredictionDecoder<*>,
    val input: Representation,
    val output: Representation,
    val prediction: Prediction<*>,
)

/**
 * Internal processing topology used by a running Skynvættr.
 *
 * The contract is intentionally small. Implementations may be linear pipelines or arbitrary directed
 * graphs with fan-out, feedback, stateful components, independent update schedules, and learned
 * modules. Models remain graph components; trainers discover them and their completed executions
 * through this boundary rather than receiving a separately wired model instance.
 */
fun interface ProcessingGraph {
    fun sense(samples: List<Sample<*>>)

    /** Models currently owned by this graph. */
    fun models(): List<Model> = emptyList()

    /** Prediction-producing model executions from the most recently completed sense cycle. */
    fun predictionExecutions(): List<PredictionExecution> = emptyList()
}
