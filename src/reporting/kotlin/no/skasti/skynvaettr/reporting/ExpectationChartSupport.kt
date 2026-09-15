package no.skasti.skynvaettr.reporting

import java.time.Duration
import java.time.Instant
import no.skasti.skynvaettr.expectations.Expectation

/**
 * Converts numeric expectations into chart overlays without coupling reporting to a model or trainer.
 *
 * An expectation is visualized as the belief developing away from the observed value that existed
 * when it formed: the line starts at [Expectation.signalInitialValue] at [Expectation.formedAt] and
 * reaches the current [Expectation.value] when the expectation stops being active. An unresolved
 * expectation remains active through the end of the requested chart window.
 */
object ExpectationChartSupport {
    fun overlay(
        expectation: Expectation<out Number>,
        label: String,
        windowStart: Instant,
        windowEnd: Instant,
    ): SampleChartRenderer.OverlaySeries? {
        require(!windowEnd.isBefore(windowStart)) { "windowEnd must not precede windowStart" }

        val activeUntil = expectation.result?.timestamp ?: windowEnd
        val lineEnd = minOf(activeUntil, windowEnd)
        if (lineEnd.isBefore(windowStart) || expectation.formedAt.isAfter(windowEnd)) return null

        val fromTime = expectation.formedAt
        val toTime = activeUntil
        val fromValue = expectation.signalInitialValue.toDouble()
        val toValue = expectation.value.toDouble()

        val visibleStart = maxOf(fromTime, windowStart)
        val visibleEnd = minOf(toTime, windowEnd)
        if (visibleEnd.isBefore(visibleStart)) return null

        val points = if (fromTime == toTime) {
            listOf(SampleChartRenderer.Point(visibleStart, toValue))
        } else {
            listOf(
                SampleChartRenderer.Point(
                    timestamp = visibleStart,
                    value = interpolate(fromTime, fromValue, toTime, toValue, visibleStart),
                ),
                SampleChartRenderer.Point(
                    timestamp = visibleEnd,
                    value = interpolate(fromTime, fromValue, toTime, toValue, visibleEnd),
                ),
            )
        }

        val resultSuffix = expectation.result?.let { " (${it.value})" }.orEmpty()
        return SampleChartRenderer.OverlaySeries(
            label = "$label$resultSuffix",
            points = points,
        )
    }

    fun overlays(
        expectations: List<Expectation<out Number>>,
        label: (index: Int, expectation: Expectation<out Number>) -> String = { index, _ -> "Expectation ${index + 1}" },
        windowStart: Instant,
        windowEnd: Instant,
    ): List<SampleChartRenderer.OverlaySeries> =
        expectations.mapIndexedNotNull { index, expectation ->
            overlay(
                expectation = expectation,
                label = label(index, expectation),
                windowStart = windowStart,
                windowEnd = windowEnd,
            )
        }

    private fun interpolate(
        fromTime: Instant,
        fromValue: Double,
        toTime: Instant,
        toValue: Double,
        at: Instant,
    ): Double {
        val totalNanos = Duration.between(fromTime, toTime).toNanos().toDouble()
        if (totalNanos == 0.0) return toValue
        val elapsedNanos = Duration.between(fromTime, at).toNanos().toDouble()
        val fraction = (elapsedNanos / totalNanos).coerceIn(0.0, 1.0)
        return fromValue + (toValue - fromValue) * fraction
    }
}
