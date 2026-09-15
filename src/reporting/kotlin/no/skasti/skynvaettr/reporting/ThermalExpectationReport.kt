package no.skasti.skynvaettr.reporting

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.Locale
import no.skasti.skynvaettr.examples.ThermalExpectationLearning

/** Renders prediction and candidate-attention diagnostics for the minimal thermal example. */
object ThermalExpectationReport {
    private val reportDays = listOf(1L, 3L, 5L, 10L)

    @JvmStatic
    fun main(args: Array<String>) {
        val reportDir = Path.of(args.firstOrNull() ?: "build/reports/examples/thermal-expectation")
        Files.createDirectories(reportDir)

        val learning = ThermalExpectationLearning()
        val world = learning.world
        val duration = Duration.ofDays(10)

        world.simulate(
            duration = duration,
            step = Duration.ofMinutes(5),
            onSense = learning.vaettr::sense,
        )

        val sampleRenderer = SampleChartRenderer()
        val series = listOf(
            SampleChartRenderer.Series(world.outdoorTemperature.id, "Outdoor temperature"),
            SampleChartRenderer.Series(world.indoorTemperature.id, "Indoor temperature"),
        )
        val finalDayStart = Instant.EPOCH.plus(Duration.ofDays(9))
        val finalDayEnd = Instant.EPOCH.plus(duration)
        sampleRenderer.render(
            title = "Thermal world — day 10",
            yAxisTitle = "Temperature (°C)",
            samples = learning.sampleStore.get(finalDayStart, finalDayEnd.plusNanos(1)),
            series = series,
            output = reportDir.resolve("day.png"),
        )

        val signals = learning.decoder.knownSignals().map { it.id }.sortedBy { it.value }
        val records = learning.predictionTrainer.records
        val dayMetrics = reportDays.associateWith { day ->
            val dayRecords = TransitionPredictionReportSupport.recordsForDay(records, day)
            TransitionPredictionReportSupport.renderAttentionHeatmap(
                title = "Thermal candidate attention relationships — day $day",
                records = dayRecords,
                signals = signals,
                output = reportDir.resolve("attention-day-$day.png"),
            )
            TransitionPredictionReportSupport.renderTargetSignalHeatmap(
                title = "Thermal next-transition target signal — day $day",
                records = dayRecords,
                signals = signals,
                output = reportDir.resolve("target-signal-day-$day.png"),
            )
            TransitionPredictionReportSupport.metrics(dayRecords)
        }

        val metricRows = reportDays.joinToString("\n") { day ->
            val metrics = dayMetrics.getValue(day)
            "| $day | ${metrics.records} | ${percent(metrics.signalAccuracy)} | ${number(metrics.valueMae)} |"
        }

        val summary = listOf(
            """
            ## Thermal transition-prediction example

            Ten simulated days using only `sensor.outdoor.temperature` and `sensor.indoor.temperature` as observations.
            Outdoor temperature follows a daily sine wave and indoor temperature follows it gradually with thermal inertia.
            No relationship between the two signals is configured in the learner.

            The example intentionally does **not** create Expectations. As in the kitchen example, every observed numeric
            signal is scored as a dynamic next-transition candidate with shared Q/K/V projections and shared score/value
            heads. Signal identity is trained with cross-entropy; value loss is applied to the observed target candidate.

            Snapshots for days **1, 3, 5 and 10** show:

            - average candidate attention by **candidate/query signal → attended history signal**;
            - normalized actual-vs-predicted next-transition signal matrices.

            | Day | Resolved predictions | Target-signal accuracy | Value MAE |
            | ---: | ---: | ---: | ---: |
            """.trimIndent(),
            metricRows,
            """

            Total resolved transition predictions: **${records.size}**.
            Model training examples: **${learning.model.trainingExampleCount}**.
            Final moving training loss: **${number(learning.model.exponentialMovingLoss ?: Double.NaN)}**.

            Thermal is deliberately a harder interpretation case than kitchen because both signals move continuously.
            Attention remains diagnostic evidence of learned context use rather than proof of causality.
            """.trimIndent(),
        ).joinToString("\n") + "\n"

        Files.writeString(reportDir.resolve("summary.md"), summary)
    }

    private fun percent(value: Double): String = String.format(Locale.ROOT, "%.1f%%", value * 100.0)

    private fun number(value: Double): String =
        if (value.isFinite()) String.format(Locale.ROOT, "%.4f", value) else "n/a"
}
