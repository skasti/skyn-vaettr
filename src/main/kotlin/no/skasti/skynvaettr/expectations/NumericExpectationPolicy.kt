package no.skasti.skynvaettr.expectations

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.SignalId

/**
 * Small horizon-free default policy for continuous numeric signals.
 *
 * Material model predictions become directional expectations. When the model is not yet confident
 * enough, the policy may bootstrap learning from an observed local movement by opening a low-
 * confidence exploratory directional expectation. Stability expectations remain independently
 * configurable.
 *
 * Thresholds are expressed as fractions of each signal's own observed range with an absolute range
 * floor. This is a deliberately simple default, not a claim that these rules are generally optimal.
 */
class NumericExpectationPolicy(
    private val minimumPredictionConfidence: Double = 0.20,
    private val materialPredictionFraction: Double = 0.04,
    private val fulfilledToleranceFraction: Double = 0.025,
    private val stabilityViolationFraction: Double = 0.035,
    private val oppositeMovementFraction: Double = 0.010,
    private val bootstrapMovementFraction: Double = 0.001,
    private val bootstrapConfidence: Double = 0.05,
    private val rangeFloor: Double = 1.0,
    private val createStabilityExpectations: Boolean = true,
    private val createBootstrapExpectations: Boolean = true,
) : ExpectationPolicy<Double> {
    private data class ObservedRange(
        var minimum: Double = Double.POSITIVE_INFINITY,
        var maximum: Double = Double.NEGATIVE_INFINITY,
    )

    private val observedRanges = mutableMapOf<SignalId, ObservedRange>()

    init {
        require(minimumPredictionConfidence in 0.0..1.0)
        require(materialPredictionFraction > 0.0)
        require(fulfilledToleranceFraction > 0.0)
        require(stabilityViolationFraction > 0.0)
        require(oppositeMovementFraction > 0.0)
        require(bootstrapMovementFraction >= 0.0)
        require(bootstrapConfidence in 0.0..1.0)
        require(rangeFloor > 0.0 && rangeFloor.isFinite())
    }

    override fun supports(prediction: Prediction<*>): Boolean = prediction.value is Double

    override fun open(
        prediction: Prediction<Double>,
        previous: Sample<Double>?,
        current: Sample<Double>,
    ): Expectation<Double>? {
        previous?.let(::observe)
        observe(current)

        val scale = observedRange(current.signal.id)
        val materialDifference = scale * materialPredictionFraction
        val usePrediction =
            prediction.confidence >= minimumPredictionConfidence &&
                abs(prediction.value - current.value) >= materialDifference

        if (usePrediction) {
            return Expectation(
                signal = current.signal,
                signalInitialValue = current.value,
                value = prediction.value,
                formedAt = current.timestamp,
                confidence = prediction.confidence,
            )
        }

        if (createBootstrapExpectations && previous != null) {
            val movement = current.value - previous.value
            val minimumBootstrapMovement = scale * bootstrapMovementFraction
            if (abs(movement) >= minimumBootstrapMovement && abs(movement) > 1e-12) {
                val bootstrapDistance = max(abs(movement), materialDifference)
                return Expectation(
                    signal = current.signal,
                    signalInitialValue = current.value,
                    value = current.value + sign(movement) * bootstrapDistance,
                    formedAt = current.timestamp,
                    confidence = bootstrapConfidence,
                )
            }
        }

        if (!createStabilityExpectations) return null

        return Expectation(
            signal = current.signal,
            signalInitialValue = current.value,
            value = current.value,
            formedAt = current.timestamp,
            confidence = 0.0,
        )
    }

    override fun assess(
        expectation: Expectation<Double>,
        previous: Sample<Double>?,
        current: Sample<Double>,
    ): ExpectationAssessment<Double>? {
        observe(current)
        val scale = observedRange(current.signal.id)
        val isStabilityExpectation =
            abs(expectation.expectationInitialValue - expectation.signalInitialValue) <= 1e-12

        if (isStabilityExpectation) {
            val surprise = abs(current.value - expectation.signalInitialValue) / scale
            if (surprise >= stabilityViolationFraction) {
                return ExpectationAssessment(
                    result = ExpectationResult("Violated", current.timestamp),
                    observedValue = current.value,
                    priority = surprise,
                )
            }
            return null
        }

        val distance = abs(current.value - expectation.value)
        if (distance / scale <= fulfilledToleranceFraction) {
            return ExpectationAssessment(
                result = ExpectationResult("Fulfilled", current.timestamp),
                observedValue = current.value,
                priority = 0.0,
            )
        }

        if (previous != null) {
            val expectedDirection = sign(expectation.expectationInitialValue - expectation.signalInitialValue)
            val progress = expectedDirection * (current.value - previous.value)
            val oppositeMovement = max(0.0, -progress / scale)
            if (oppositeMovement >= oppositeMovementFraction) {
                val miss = distance / scale
                return ExpectationAssessment(
                    result = ExpectationResult("Violated", current.timestamp),
                    observedValue = current.value,
                    priority = max(oppositeMovement, miss),
                )
            }
        }

        return null
    }

    private fun observe(sample: Sample<Double>) {
        require(sample.value.isFinite()) { "Observed numeric values must be finite" }
        val range = observedRanges.getOrPut(sample.signal.id, ::ObservedRange)
        range.minimum = minOf(range.minimum, sample.value)
        range.maximum = maxOf(range.maximum, sample.value)
    }

    private fun observedRange(signalId: SignalId): Double {
        val range = observedRanges[signalId] ?: return rangeFloor
        return max(range.maximum - range.minimum, rangeFloor)
    }
}
