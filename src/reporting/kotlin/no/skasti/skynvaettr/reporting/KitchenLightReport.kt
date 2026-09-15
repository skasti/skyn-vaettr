package no.skasti.skynvaettr.reporting

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.Locale
import no.skasti.skynvaettr.examples.KitchenLightLearning

/** Renders prediction and attention diagnostics for the dimmer/light temporal-relation example. */
object KitchenLightReport {
    private val reportDays = listOf(1L, 3L, 5L, 10L)

    @JvmStatic
    fun main(args: Array<String>) {
        val reportDir = Path.of(args.firstOrNull() ?: "build/reports/examples/kitchen-light")
        Files.createDirectories(reportDir)

        val learning = KitchenLightLearning()
        val world = learning.world
        val duration = Duration.ofDays(10)

        world.simulate(
            duration = duration,
            step = Duration.ofMinutes(5),
            onSense = learning.vaettr::sense,
        )

        val sampleRenderer = SampleChartRenderer()
        val series = listOf(
            SampleChartRenderer.Series(world.dimmer.id, "Kitchen dimmer"),
            SampleChartRenderer.Series(world.light.id, "Kitchen light"),
        )
        val finalDayStart = Instant.EPOCH.plus(Duration.ofDays(9))
        val finalDayEnd = Instant.EPOCH.plus(duration)
        sampleRenderer.render(
            title = "Kitchen dimmer → light — day 10",
            yAxisTitle = "Level",
            samples = learning.sampleStore.get(finalDayStart, finalDayEnd.plusNanos(1)),
            series = series,
            output = reportDir.resolve("day.png"),
        )

        val signals = learning.decoder.knownSignals().map { it.id }.sortedBy { it.value }
        val records = learning.predictionTrainer.records
        val dayMetrics = reportDays.associateWith { day ->
            val dayRecords = TransitionPredictionReportSupport.recordsForDay(records, day)
            TransitionPredictionReportSupport.renderAttentionHeatmap(
                title = "Kitchen attention by signal / age — day $day",
                records = dayRecords,
                signals = signals,
                output = reportDir.resolve("attention-day-$day.png"),
            )
            TransitionPredictionReportSupport.renderTargetSignalHeatmap(
                title = "Kitchen next-transition target signal — day $day",
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
            ## Kitchen dimmer/light prediction example

            Ten simulated days with two numeric signals: `state.kitchen.dimmer` and `state.kitchen.light`.
            The dimmer changes only a few times during daytime and the light follows it with a fixed **10 minute delay**.
            Skynvættr is not told this relationship.

            The example intentionally does **not** create Expectations. A shared learnable single-query Q/K/V attention
            model predicts the **next meaningful numeric transition** as a horizon-free `Prediction<Double>` whose target
            signal is itself decoded from the model's latent output. When the next transition is observed, its signal and
            value supervise the model directly.

            The reports show snapshots for days **1, 3, 5 and 10**:

            - `attention-day-N.png` aggregates the attention mass by source signal and observation age for predictions
              resolved during that day. The age buckets are reporting-only; the model receives actual relative times.
            - `target-signal-day-N.png` is a normalized actual-vs-predicted signal matrix. Increasing diagonal mass means
              the model is learning which signal transitions next.

            | Day | Resolved predictions | Target-signal accuracy | Value MAE |
            | ---: | ---: | ---: | ---: |
            """.trimIndent(),
            metricRows,
            """

            Total resolved transition predictions: **${records.size}**.
            Model training examples: **${learning.model.trainingExampleCount}**.
            Final moving training loss: **${number(learning.model.exponentialMovingLoss ?: Double.NaN)}**.

            Attention is diagnostic evidence of what context the model uses, not proof of causality. The important kitchen
            test is whether attention increasingly concentrates on recent dimmer history before light transitions while the
            target-signal matrix moves toward correct `state.kitchen.light` predictions after dimmer changes.
            """.trimIndent(),
        ).joinToString("\n") + "\n"

        Files.writeString(reportDir.resolve("summary.md"), summary)
    }

    private fun percent(value: Double): String = String.format(Locale.ROOT, "%.1f%%", value * 100.0)

    private fun number(value: Double): String =
        if (value.isFinite()) String.format(Locale.ROOT, "%.4f", value) else "n/a"
}
