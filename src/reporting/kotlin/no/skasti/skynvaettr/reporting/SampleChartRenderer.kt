package no.skasti.skynvaettr.reporting

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.SignalId
import org.knowm.xchart.BitmapEncoder
import org.knowm.xchart.XYChartBuilder
import org.knowm.xchart.XYSeries

/**
 * Generic numeric sample-series renderer extracted from the plotting approach used in vaettr-playpen.
 *
 * The renderer intentionally knows nothing about a specific world or experiment. Callers provide
 * timestamped [Sample] values plus the signals they want plotted. Additional timestamped numeric
 * [OverlaySeries] can be used for derived values such as predictions or expectations without
 * pretending that those values are observations.
 */
class SampleChartRenderer {
    data class Series(
        val signal: SignalId,
        val label: String = signal.value,
    )

    data class Point(
        val timestamp: Instant,
        val value: Double,
    )

    data class OverlaySeries(
        val label: String,
        val points: List<Point>,
    )

    fun render(
        title: String,
        yAxisTitle: String,
        samples: List<Sample<*>>,
        series: List<Series>,
        output: Path,
        overlays: List<OverlaySeries> = emptyList(),
    ) {
        require(samples.isNotEmpty()) { "samples must not be empty" }
        require(series.isNotEmpty() || overlays.isNotEmpty()) { "at least one series is required" }

        val firstTimestamp = buildList {
            addAll(samples.map { it.timestamp })
            overlays.forEach { overlay -> addAll(overlay.points.map { it.timestamp }) }
        }.minOrNull() ?: error("no chart points available")

        val chart = XYChartBuilder()
            .width(1280)
            .height(720)
            .title(title)
            .xAxisTitle("Elapsed hours")
            .yAxisTitle(yAxisTitle)
            .build()

        chart.styler.isLegendVisible = true
        chart.styler.markerSize = 0

        series.forEach { requested ->
            val points = samples
                .asSequence()
                .filter { it.signal.id == requested.signal }
                .mapNotNull { sample ->
                    val value = sample.value as? Number ?: return@mapNotNull null
                    sample.timestamp to value.toDouble()
                }
                .sortedBy { it.first }
                .toList()

            require(points.isNotEmpty()) { "no numeric samples found for ${requested.signal.value}" }
            addLineSeries(chart, requested.label, firstTimestamp, points)
        }

        overlays.forEach { overlay ->
            require(overlay.points.isNotEmpty()) { "overlay series ${overlay.label} must contain points" }
            addLineSeries(
                chart = chart,
                label = overlay.label,
                firstTimestamp = firstTimestamp,
                points = overlay.points
                    .sortedBy { it.timestamp }
                    .map { it.timestamp to it.value },
            )
        }

        Files.createDirectories(output.parent)
        BitmapEncoder.saveBitmap(chart, output.toString(), BitmapEncoder.BitmapFormat.PNG)
    }

    private fun addLineSeries(
        chart: org.knowm.xchart.XYChart,
        label: String,
        firstTimestamp: Instant,
        points: List<Pair<Instant, Double>>,
    ) {
        chart.addSeries(
            label,
            points.map { (timestamp, _) -> elapsedHours(firstTimestamp, timestamp) },
            points.map { (_, value) -> value },
        ).xySeriesRenderStyle = XYSeries.XYSeriesRenderStyle.Line
    }

    private fun elapsedHours(firstTimestamp: Instant, timestamp: Instant): Double =
        Duration.between(firstTimestamp, timestamp).toNanos().toDouble() /
            Duration.ofHours(1).toNanos().toDouble()
}
