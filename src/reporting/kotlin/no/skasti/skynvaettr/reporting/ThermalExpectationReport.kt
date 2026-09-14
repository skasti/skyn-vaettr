package no.skasti.skynvaettr.reporting

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import no.skasti.skynvaettr.examples.ThermalExpectationLearning
import no.skasti.skynvaettr.expectations.Expectation

/** Renders the inspectable report for the minimal thermal expectation example. */
object ThermalExpectationReport {
    @JvmStatic
    fun main(args: Array<String>) {
        val reportDir = Path.of(args.firstOrNull() ?: "build/reports/examples/thermal-expectation")
        Files.createDirectories(reportDir)

        val learning = ThermalExpectationLearning()
        val world = learning.world
        val duration = Duration.ofDays(3)
        val reportStart = Instant.EPOCH.plus(Duration.ofDays(2))
        val reportEnd = Instant.EPOCH.plus(duration)

        world.simulate(
            duration = duration,
            step = Duration.ofMinutes(5),
        ) { samples ->
            learning.vaettr.sense(samples)
        }

        val samples = learning.sampleStore.get(
            reportStart,
            reportEnd.plusNanos(1),
        )

        val renderer = SampleChartRenderer()
        val worldSeries = listOf(
            SampleChartRenderer.Series(world.outdoorTemperature.id, "Outdoor temperature"),
            SampleChartRenderer.Series(world.indoorTemperature.id, "Indoor temperature"),
        )

        renderer.render(
            title = "Thermal expectation example — final simulated day",
            yAxisTitle = "Temperature (°C)",
            samples = samples,
            series = worldSeries,
            output = reportDir.resolve("day.png"),
        )

        val expectationSeries = expectationOverlays(
            expectations = learning.trainer.expectations,
            reportStart = reportStart,
            reportEnd = reportEnd,
        )
        renderer.render(
            title = "Thermal expectation example — learned expectations",
            yAxisTitle = "Temperature (°C)",
            samples = samples,
            series = worldSeries,
            overlays = expectationSeries,
            output = reportDir.resolve("expectations.png"),
        )

        val resolved = learning.trainer.expectations.count { it.result != null }
        val fulfilled = learning.trainer.expectations.count { it.result?.value == "Fulfilled" }
        val violated = learning.trainer.expectations.count { it.result?.value == "Violated" }
        val experiences = learning.trainer.experiences

        Files.writeString(
            reportDir.resolve("summary.md"),
            """
            ## Thermal expectation example

            Three simulated days using only `sensor.outdoor.temperature` and `sensor.indoor.temperature` as observations.
            The graphs show the final day after the default model has accumulated expectation-derived experience.

            The model emits horizon-free predictions. `ExpectationTrainer` turns those into persistent expectations,
            lets the lifecycle policy resolve them from later observations, then trains on the value observed at the
            actual resolution point. No +1/+5/+10 minute target exists in this example.

            Resolved expectations: **$resolved** (`Fulfilled`: **$fulfilled**, `Violated`: **$violated**).
            Replayable expectation episodes: **${experiences.size}**.
            Model training examples currently retained: **${learning.model.trainingExampleCount}**.

            `expectations.png` renders the expectation value for the interval where each belief was active. Completed
            series include the result in parentheses. The x-axis therefore means **when the expectation was held**, not
            a forecast target timestamp.

            Replay selection is priority-weighted. The default numeric lifecycle currently assigns high priority to
            surprising violations and low priority to fulfilled expectations. This is only a working baseline; both the
            model and lifecycle/replay policies remain replaceable generic components.
            """.trimIndent() + "\n",
        )
    }

    private fun expectationOverlays(
        expectations: List<Expectation<Double>>,
        reportStart: Instant,
        reportEnd: Instant,
    ): List<SampleChartRenderer.OverlaySeries> =
        expectations
            .filter { expectation ->
                val result = expectation.result
                !expectation.formedAt.isAfter(reportEnd) &&
                    (result == null || !result.timestamp.isBefore(reportStart))
            }
            .mapIndexed { index, expectation ->
                val result = expectation.result
                val start = maxOf(expectation.formedAt, reportStart)
                val end = minOf(result?.timestamp ?: reportEnd, reportEnd)
                val resultSuffix = result?.let { " (${it.value})" }.orEmpty()

                SampleChartRenderer.OverlaySeries(
                    label = "Expectation ${index + 1}$resultSuffix",
                    points = listOf(
                        SampleChartRenderer.Point(start, expectation.value),
                        SampleChartRenderer.Point(end, expectation.value),
                    ),
                )
            }
}
