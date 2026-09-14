package no.skasti.skynvaettr.examples

import java.time.Instant
import no.skasti.skynvaettr.Vaettr
import no.skasti.skynvaettr.expectations.NumericExpectationPolicy
import no.skasti.skynvaettr.models.OnlineKnnPredictionModel
import no.skasti.skynvaettr.signals.InMemorySampleStore
import no.skasti.skynvaettr.signals.SampleStore
import no.skasti.skynvaettr.signals.Signal
import no.skasti.skynvaettr.training.ExpectationExperience
import no.skasti.skynvaettr.training.ExpectationTrainer
import no.skasti.skynvaettr.training.WeightedPriorityReplaySelector

/**
 * Example wiring for [ThermalExpectationScenario].
 *
 * All learning components are general runtime components. This class only chooses which signals are
 * model inputs and how the thermal values are scaled into a small numeric feature vector.
 */
class ThermalExpectationLearning(
    val world: ThermalExpectationScenario = ThermalExpectationScenario(),
) {
    val sampleStore = InMemorySampleStore()
    val model = OnlineKnnPredictionModel(
        signal = world.indoorTemperature,
        inputDimensions = INPUT_DIMENSIONS,
    )
    val trainer = ExpectationTrainer(
        sampleStore = sampleStore,
        targetSignal = world.indoorTemperature,
        model = model,
        policy = NumericExpectationPolicy(),
        episodeSignals = listOf(world.outdoorTemperature, world.indoorTemperature),
        inputAt = ::inputAt,
        replaySelector = WeightedPriorityReplaySelector<ExpectationExperience<DoubleArray, Double>>(
            priority = { it.priority },
            seed = 17,
        ),
    )
    val vaettr = Vaettr(
        sampleStore = sampleStore,
        trainers = listOf(trainer),
    )

    private fun inputAt(
        store: SampleStore,
        at: Instant,
    ): DoubleArray? {
        val indoor = numericValue(store, world.indoorTemperature, at) ?: return null
        val outdoor = numericValue(store, world.outdoorTemperature, at) ?: return null
        val previousIndoor = previousNumericValue(store, world.indoorTemperature, at) ?: return null
        val previousOutdoor = previousNumericValue(store, world.outdoorTemperature, at) ?: return null

        return doubleArrayOf(
            indoor / TEMPERATURE_SCALE,
            outdoor / TEMPERATURE_SCALE,
            (indoor - previousIndoor) / DELTA_SCALE,
            (outdoor - previousOutdoor) / DELTA_SCALE,
        )
    }

    private fun numericValue(
        store: SampleStore,
        signal: Signal<Double>,
        at: Instant,
    ): Double? =
        (store.latestAtOrBefore(signal.id, at)?.value as? Number)?.toDouble()

    private fun previousNumericValue(
        store: SampleStore,
        signal: Signal<Double>,
        at: Instant,
    ): Double? =
        store.get(Instant.MIN, at, signal.id)
            .lastOrNull()
            ?.value
            .let { it as? Number }
            ?.toDouble()

    companion object {
        private const val INPUT_DIMENSIONS = 4
        private const val TEMPERATURE_SCALE = 30.0
        private const val DELTA_SCALE = 5.0
    }
}
