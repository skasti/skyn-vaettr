package no.skasti.skynvaettr.training

import kotlin.math.abs
import kotlin.math.max
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.SignalId

/** One meaningful observed change in a numeric signal. */
data class NumericTransition(
    val previous: Sample<Double>,
    val current: Sample<Double>,
)

/**
 * Detects meaningful numeric state changes without treating every small continuous movement as an
 * event. Change is measured from the last accepted transition anchor rather than only from the
 * immediately preceding sample, so gradual thermal movement eventually accumulates into a
 * transition while exact kitchen-light plateaus stay silent.
 */
class NumericTransitionDetector(
    private val rangeFraction: Double = 0.05,
    private val rangeFloor: Double = 1.0,
) {
    private data class State(
        var minimum: Double,
        var maximum: Double,
        var lastSample: Sample<Double>,
        var anchor: Sample<Double>,
    )

    private val states = mutableMapOf<SignalId, State>()

    init {
        require(rangeFraction > 0.0)
        require(rangeFloor > 0.0 && rangeFloor.isFinite())
    }

    fun observe(sample: Sample<Double>): NumericTransition? {
        require(sample.value.isFinite()) { "numeric transition values must be finite" }
        val state = states[sample.signal.id]
        if (state == null) {
            states[sample.signal.id] = State(
                minimum = sample.value,
                maximum = sample.value,
                lastSample = sample,
                anchor = sample,
            )
            return null
        }

        state.minimum = minOf(state.minimum, sample.value)
        state.maximum = maxOf(state.maximum, sample.value)
        val scale = max(state.maximum - state.minimum, rangeFloor)
        val meaningfulChange = abs(sample.value - state.anchor.value) >= scale * rangeFraction
        val previous = state.lastSample
        state.lastSample = sample

        if (!meaningfulChange) return null

        state.anchor = sample
        return NumericTransition(previous = previous, current = sample)
    }
}
