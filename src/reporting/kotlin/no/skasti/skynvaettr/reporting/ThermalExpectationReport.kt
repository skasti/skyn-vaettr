package no.skasti.skynvaettr.reporting

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.Locale
import no.skasti.skynvaettr.examples.ThermalExpectationLearning
import no.skasti.skynvaettr.expectations.Expectation
import no.skasti.skynvaettr.models.OnlineKnnModel
import no.skasti.skynvaettr.signals.SignalId
import no.skasti.skynvaettr.training.LearningObjective

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
        val finalDaySamples = learning.sampleStore.get(finalDayStart, finalDayEnd.plusNanos(1))

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
            val samples = learning.sampleStore.get(reportStart, reportEnd.plusNanos(1))
            val expectations = checkNotNull(expectationSnapshots[day]) {
                "Expected an expectation snapshot at the end of simulated day $day"
            }

            renderer.render(
                title = "Thermal expectation example — learned expectations, day $day",
                yAxisTitle = "Temperature (°C)",
                samples = samples,
                series = worldSeries,
                overlays = expectationOverlays(expectations, reportStart, reportEnd),
                output = reportDir.resolve("expectations-day-$day.png"),
            )
        }

        val resolved = learning.trainer.expectations.count { it.result != null }
        val fulfilled = learning.trainer.expectations.count { it.result?.value == "Fulfilled" }
        val violated = learning.trainer.expectations.count { it.result?.value == "Violated" }
        val experiences = learning.trainer.experiences
        val models = learning.graph.models().filterIsInstance<OnlineKnnModel>()
        val retainedTrainingExamples = models.sumOf { it.trainingExampleCount }
        val objectiveSummary = listOf(world.outdoorTemperature.id, world.indoorTemperature.id)
            .joinToString(separator = "\n") { signalId ->
                "- `${signalId.value}`: ${objectiveWeights(learning, signalId)}"
            }

        Files.writeString(
            reportDir.resolve("summary.md"),
            """
            ## Thermal expectation example

            Ten simulated days using only `sensor.outdoor.temperature` and `sensor.indoor.temperature` as observations.
            Neither signal is configured as a prediction target. The graph discovers one numeric prediction route per
            observed compatible signal, while each predictor consumes the same complete sensory `Representation`.

            Continuous and transition self-supervision run in parallel. Objective weights are adapted from predictive
            skill relative to a no-change persistence baseline rather than being assigned from signal names or domain
            metadata. Expectations remain a separate belief layer.

            Learned expectation graphs are shown for days **1, 3, 5 and 10** so the default models' development can be
            inspected over time. Each graph contains only that day's observations and the expectations known at the end
            of that day; later outcomes are therefore not leaked into earlier snapshots.

            After ten days: discovered numeric predictors: **${models.size}**.
            Continuous training examples: **${learning.observationTrainer.trainingExampleCount}**.
            Meaningful transitions detected: **${learning.transitionTrainer.detectedTransitionCount}**.
            Transition training examples: **${learning.transitionTrainer.trainingExampleCount}**.

            Objective state after ten days:
            $objectiveSummary

            Resolved expectations: **$resolved** (`Fulfilled`: **$fulfilled**, `Violated`: **$violated**).
            Replayable expectation episodes: **${experiences.size}**.
            Model training examples currently retained across predictors: **$retainedTrainingExamples**.

            Each expectation series runs from the signal value observed when the belief was formed
            (`signalInitialValue`) to the expectation's current `value` at its result time, or at the end of the graph
            window while it remains active. Completed series include the result in parentheses. The x-axis therefore
            means **when the expectation was held**, not a forecast target timestamp.
            """.trimIndent() + "\n",
        )
    }

    private fun objectiveWeights(
        learning: ThermalExpectationLearning,
        signalId: SignalId,
    ): String {
        val continuousWeight = learning.objectiveWeights.weight(signalId, LearningObjective.Continuous)
        val transitionWeight = learning.objectiveWeights.weight(signalId, LearningObjective.Transition)
        val continuousSkill = learning.objectiveWeights.skill(signalId, LearningObjective.Continuous)
        val transitionSkill = learning.objectiveWeights.skill(signalId, LearningObjective.Transition)
        return "continuous=${format(continuousWeight)} (skill=${format(continuousSkill)}), " +
            "transition=${format(transitionWeight)} (skill=${format(transitionSkill)})"
    }

    private fun format(value: Double?): String =
        value?.let { String.format(Locale.ROOT, "%.3f", it) } ?: "n/a"

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
