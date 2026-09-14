package no.skasti.skynvaettr.expectations

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign
import no.skasti.skynvaettr.signals.Sample

/**
 * Small horizon-free default policy for continuous numeric signals.
 *
 * Low-confidence or immaterial predictions may become stability expectations when enabled. Material
 * predictions become directional expectations toward the predicted value. Explicit expectations
 * resolve when the signal reaches the expected value or starts moving materially in the opposite
 * direction; stability expectations resolve when the signal moves far enough away from their origin.
 *
 * Thresholds are expressed as fractions of the observed range with an absolute range floor. This is
 * a deliberately simple default, not a claim that these rules are generally optimal.
 */
class NumericExpectationPolicy(
    private val minimumPredictionConfidence: Double = 0.20,
    private val materialPredictionFraction: Double = 0.04,
    private val fulfilledToleranceFraction: Double = 0.025,
    private val stabilityViolationFraction: Double = 0.035,
    private val oppositeMovementFraction: Double = 0.010,
    private val rangeFloor: Double = 1.0,
    private val createStabilityExpectations: Boolean = true,
) : ExpectationPolicy<Double> {
    private var minimumObserved = Double.POSITIVE_INFINITY
    private var maximumObserved = Double.NEGATIVE_INFINITY

    init {
        require(minimumPredictionConfidence in 0.0..1.0)
        require(materialPredictionFraction > 0.0)
        require(fulfilledToleranceFraction > 0.0)
        require(stabilityViolationFraction > 0.0)
        require(oppositeMovementFraction > 0.0)
        require(rangeFloor > 0.0 && rangeFloor.isFinite())
    }

    override fun supports(prediction: Prediction<*>): Boolean = prediction.value is Double

    override fun open(
        prediction: Prediction<Double>,
        current: Sample<Double>,
    ): Expectation<Double>? {
        observe(current.value)
        val materialDifference = observedRange() * materialPredictionFraction
        val usePrediction =
            prediction.confidence >= minimumPredictionConfidence &&
                abs(prediction.value - current.value) >= materialDifference

        if (!usePrediction && !createStabilityExpectations) {
            return null
        }

        return Expectation(
            signal = current.signal,
            signalInitialValue = current.value,
            value = if (usePrediction) prediction.value else current.value,
            formedAt = current.timestamp,
            confidence = if (usePrediction) prediction.confidence else 0.0,
        )
    }

    override fun assess(
        expectation: Expectation<Double>,
        previous: Sample<Double>?,
        current: Sample<Double>,
    ): ExpectationAssessment<Double>? {
        observe(current.value)
        val scale = observedRange()
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
            val expectedDirection =
                sign(expectation.expectationInitialValue - expectation.signalInitialValue)
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

    private fun observe(value: Double) {
        require(value.isFinite()) { "Observed numeric values must be finite" }
        minimumObserved = minOf(minimumObserved, value)
        maximumObserved = maxOf(maximumObserved, value)
    }

    private fun observedRange(): Double =
        max(maximumObserved - minimumObserved, rangeFloor)
}
