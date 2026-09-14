package no.skasti.skynvaettr.examples

import java.time.Duration
import java.time.Instant
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.Signal

/**
 * Minimal environment for learning a temporal relationship between outdoor and indoor temperature.
 *
 * Outdoor temperature follows a 24-hour sine wave from 10 to 25 °C. Indoor temperature starts at
 * the outdoor midpoint and moves toward the current outdoor temperature with a configurable
 * fraction of the remaining delta per hour. The update uses exponential retention so changing the
 * simulation step does not materially change the thermal time constant.
 *
 * The intended learning task is for a model attached to the Vaettr processing graph to develop a
 * continuously updated expectation of future sensor.indoor.temperature from accumulated history.
 * This scenario deliberately contains no hand-coded predictor and exposes only sensor samples.
 */
class ThermalExpectationScenario(
    val outdoorTemperature: Signal<Double> = Signal("sensor.outdoor.temperature"),
    val indoorTemperature: Signal<Double> = Signal("sensor.indoor.temperature"),
    private val minimumOutdoorTemperature: Double = 10.0,
    private val maximumOutdoorTemperature: Double = 25.0,
    private val hourlyDeltaFraction: Double = 0.10,
) {
    init {
        require(minimumOutdoorTemperature < maximumOutdoorTemperature)
        require(hourlyDeltaFraction > 0.0 && hourlyDeltaFraction < 1.0)
    }

    fun simulate(
        duration: Duration,
        step: Duration,
        start: Instant = Instant.EPOCH,
        onSense: (List<Sample<*>>) -> Unit,
    ) {
        require(!duration.isNegative && !duration.isZero)
        require(!step.isNegative && !step.isZero)

        val stepHours = step.toNanos().toDouble() / Duration.ofHours(1).toNanos().toDouble()
        val stepFraction = 1.0 - (1.0 - hourlyDeltaFraction).pow(stepHours)
        val totalSteps = duration.toNanos() / step.toNanos()

        var indoor = midpoint(minimumOutdoorTemperature, maximumOutdoorTemperature)

        for (index in 0..totalSteps) {
            val timestamp = start.plus(step.multipliedBy(index))
            val elapsedHours = Duration.between(start, timestamp).toNanos().toDouble() /
                Duration.ofHours(1).toNanos().toDouble()
            val outdoor = outdoorTemperatureAt(elapsedHours)

            onSense(
                listOf(
                    Sample(outdoorTemperature, outdoor, timestamp),
                    Sample(indoorTemperature, indoor, timestamp),
                ),
            )

            indoor += (outdoor - indoor) * stepFraction
        }
    }

    private fun outdoorTemperatureAt(elapsedHours: Double): Double {
        val midpoint = midpoint(minimumOutdoorTemperature, maximumOutdoorTemperature)
        val amplitude = (maximumOutdoorTemperature - minimumOutdoorTemperature) / 2.0
        // Minimum at midnight, maximum at noon.
        return midpoint + amplitude * sin(2.0 * PI * (elapsedHours - 6.0) / 24.0)
    }

    private fun midpoint(minimum: Double, maximum: Double): Double = (minimum + maximum) / 2.0
}
