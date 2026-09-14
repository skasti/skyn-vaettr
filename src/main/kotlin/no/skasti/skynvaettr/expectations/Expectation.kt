package no.skasti.skynvaettr.expectations

import java.time.Instant

/**
 * A persistent commitment to a [Prediction].
 *
 * An expectation is created by an external expectation gate after deciding that a model prediction
 * is worth believing. [cost] expresses how expensive that commitment is, while [reward] expresses
 * how valuable fulfillment would be. [confidence] starts from the prediction confidence and may
 * increase when the model later repeats the same prediction with greater confidence.
 *
 * Thresholds, gate policy, lifecycle, fulfillment and learning policy remain outside this data type.
 */
data class Expectation<T>(
    val prediction: Prediction<T>,
    val formedAt: Instant,
    val cost: Double,
    val reward: Double,
    val confidence: Double = prediction.confidence,
) {
    init {
        require(!formedAt.isBefore(prediction.createdAt)) {
            "Expectation cannot be formed before its prediction was created"
        }
        require(cost.isFinite() && cost >= 0.0) {
            "Expectation cost must be finite and non-negative"
        }
        require(reward.isFinite() && reward >= 0.0) {
            "Expectation reward must be finite and non-negative"
        }
        require(confidence.isFinite() && confidence in 0.0..1.0) {
            "Expectation confidence must be finite and between 0 and 1"
        }
        require(confidence >= prediction.confidence) {
            "Expectation confidence cannot be lower than its prediction confidence"
        }
    }

    /**
     * Reinforce this expectation with a later model prediction for the same signal and value.
     *
     * Whether reinforcement should happen is a gate-policy decision. This helper only preserves the
     * data-model invariants once the gate has made that decision.
     */
    fun reinforcedBy(newPrediction: Prediction<T>): Expectation<T> {
        require(newPrediction.signal == prediction.signal) {
            "Reinforcement prediction must belong to the same signal"
        }
        require(newPrediction.value == prediction.value) {
            "Reinforcement prediction must predict the same value"
        }
        require(!newPrediction.createdAt.isBefore(prediction.createdAt)) {
            "Reinforcement prediction cannot predate the current prediction"
        }

        return copy(
            prediction = newPrediction,
            confidence = maxOf(confidence, newPrediction.confidence),
        )
    }
}
