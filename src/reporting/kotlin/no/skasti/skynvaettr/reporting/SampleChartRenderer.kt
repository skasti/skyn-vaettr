package no.skasti.skynvaettr.reporting

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.SignalId
import org.knowm.xchart.BitmapEncoder
import org.knowm.xchart.XYChartBuilder
import org.knowm.xchart.XYSeries

/**
 * Generic numeric sample-series renderer extracted from the plotting approach used in vaettr-playpen.
 *
 * The renderer intentionally knows nothing about a specific world or experiment. Callers provide
 * timestamped [Sample] values plus the signals they want plotted. Samples are plotted at their
 * actual observation time relative to the first sample in the chart.
 */
class SampleChartRenderer {
    data class Series(
        val signal: SignalId,
        val label: String = signal.value,
    )

    fun render(
        title: String,
        yAxisTitle: String,
        samples: List<Sample<*>>,
        series: List<Series>,
        output: Path,
    ) {
        require(samples.isNotEmpty()) { "samples must not be empty" }
        require(series.isNotEmpty()) { "at least one series is required" }

        val firstTimestamp = samples.minOf { it.timestamp }
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
                    val elapsedHours = Duration.between(firstTimestamp, sample.timestamp).toNanos().toDouble() /
                        Duration.ofHours(1).toNanos().toDouble()
                    elapsedHours to value.toDouble()
                }
                .sortedBy { it.first }
                .toList()

            require(points.isNotEmpty()) { "no numeric samples found for ${requested.signal.value}" }

            chart.addSeries(
                requested.label,
                points.map { it.first },
                points.map { it.second },
            ).xySeriesRenderStyle = XYSeries.XYSeriesRenderStyle.Line
        }

        Files.createDirectories(output.parent)
        BitmapEncoder.saveBitmap(chart, output.toString(), BitmapEncoder.BitmapFormat.PNG)
    }
}
