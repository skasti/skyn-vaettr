package no.skasti.skynvaettr.reporting

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import no.skasti.skynvaettr.examples.ThermalExpectationLearning
import no.skasti.skynvaettr.expectations.Expectation
import no.skasti.skynvaettr.models.OnlineKnnModel

/** Renders the inspectable report for the minimal thermal expectation example. */
object ThermalExpectationReport {
    private val reportDays = listOf(1L, 3L, 5L, 10L)

    @JvmStatic
    fun main(args: Array<String>) {
        val reportDir = Path.of(args.firstOrNull() ?: "build/reports/examples/thermal-expectation")
        Files.createDirectories(reportDir)

        val learning = ThermalExpectationLearning()
        val world = learning.world
        val duration = Duration.ofDays(10)
        val expectationSnapshots = mutableMapOf<Long, List<Expectation<Double>>>()

        world.simulate(
            duration = duration,
            step = Duration.ofMinutes(5),
        ) { samples ->
            learning.vaettr.sense(samples)

            val timestamp = samples.firstOrNull()?.timestamp
            reportDays.forEach { day ->
                if (timestamp == Instant.EPOCH.plus(Duration.ofDays(day))) {
                    expectationSnapshots[day] = learning.trainer.expectations.toList()
                }
            }
        }

        val renderer = SampleChartRenderer()
        val worldSeries = listOf(
            SampleChartRenderer.Series(world.outdoorTemperature.id, "Outdoor temperature"),
            SampleChartRenderer.Series(world.indoorTemperature.id, "Indoor temperature"),
        )

        val finalDayStart = Instant.EPOCH.plus(Duration.ofDays(9))
        val finalDayEnd = Instant.EPOCH.plus(duration)
        val finalDaySamples = learning.sampleStore.get(
            finalDayStart,
            finalDayEnd.plusNanos(1),
        )

        renderer.render(
            title = "Thermal expectation example — day 10",
            yAxisTitle = "Temperature (°C)",
            samples = finalDaySamples,
            series = worldSeries,
            output = reportDir.resolve("day.png"),
        )

        reportDays.forEach { day ->
            val reportStart = Instant.EPOCH.plus(Duration.ofDays(day - 1))
            val reportEnd = Instant.EPOCH.plus(Duration.ofDays(day))
            val samples = learning.sampleStore.get(
                reportStart,
                reportEnd.plusNanos(1),
            )
            val expectations = checkNotNull(expectationSnapshots[day]) {
                "Expected an expectation snapshot at the end of simulated day $day"
            }

            renderer.render(
                title = "Thermal expectation example — learned expectations, day $day",
                yAxisTitle = "Temperature (°C)",
                samples = samples,
                series = worldSeries,
                overlays = expectationOverlays(
                    expectations = expectations,
                    reportStart = reportStart,
                    reportEnd = reportEnd,
                ),
                output = reportDir.resolve("expectations-day-$day.png"),
            )
        }

        val resolved = learning.trainer.expectations.count { it.result != null }
        val fulfilled = learning.trainer.expectations.count { it.result?.value == "Fulfilled" }
        val violated = learning.trainer.expectations.count { it.result?.value == "Violated" }
        val experiences = learning.trainer.experiences
        val models = learning.graph.models().filterIsInstance<OnlineKnnModel>()
        val retainedTrainingExamples = models.sumOf { it.trainingExampleCount }

        Files.writeString(
            reportDir.resolve("summary.md"),
            """
            ## Thermal expectation example

            Ten simulated days using only `sensor.outdoor.temperature` and `sensor.indoor.temperature` as observations.
            Neither signal is configured as a prediction target. The graph discovers one numeric prediction route per
            observed compatible signal, while each predictor consumes the same complete sensory `Representation`.

            Learned expectation graphs are shown for days **1, 3, 5 and 10** so the default models' development can be
            inspected over time. Each graph contains only that day's observations and the expectations known at the end
            of that day; later outcomes are therefore not leaked into earlier snapshots.

            For this run, **stability expectations are disabled**. Low-confidence models bootstrap with low-confidence
            exploratory directional expectations derived from observed movement. Once models gain experience, confident
            material decoded predictions can form normal expectations.

            Model outputs remain horizon-free. `ExpectationTrainer` turns decoded predictions into persistent
            expectations, lets the lifecycle policy resolve them from later observations, then trains on the value observed
            at the actual resolution point. No +1/+5/+10 minute target exists in this example.

            After ten days: discovered numeric predictors: **${models.size}**.
            Resolved expectations: **$resolved** (`Fulfilled`: **$fulfilled**, `Violated`: **$violated**).
            Replayable expectation episodes: **${experiences.size}**.
            Model training examples currently retained across predictors: **$retainedTrainingExamples**.

            Each expectation series runs from the signal value observed when the belief was formed
            (`signalInitialValue`) to the expectation's current `value` at its result time, or at the end of the graph
            window while it remains active. Completed series include the result in parentheses. The x-axis therefore
            means **when the expectation was held**, not a forecast target timestamp.

            Replay selection is priority-weighted. The default numeric lifecycle currently assigns high priority to
            surprising violations and low priority to fulfilled expectations. This is only a working baseline; model,
            decoder discovery, lifecycle and replay policies remain replaceable components.
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
                val start = maxOf(expectation.formedAt, reportStart)
                val end = minOf(expectation.result?.timestamp ?: reportEnd, reportEnd)
                val resultSuffix = expectation.result?.let { " (${it.value})" }.orEmpty()

                SampleChartRenderer.OverlaySeries(
                    label = "Expectation ${index + 1}$resultSuffix",
                    points = listOf(
                        SampleChartRenderer.Point(start, expectation.signalInitialValue),
                        SampleChartRenderer.Point(end, expectation.value),
                    ),
                )
            }
}
