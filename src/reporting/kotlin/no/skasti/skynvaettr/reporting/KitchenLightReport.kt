package no.skasti.skynvaettr.reporting

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.Locale
import no.skasti.skynvaettr.examples.KitchenLightLearning
import no.skasti.skynvaettr.expectations.Expectation
import no.skasti.skynvaettr.signals.SignalId
import no.skasti.skynvaettr.training.LearningObjective

/** Renders the dimmer/light temporal-relation example. */
object KitchenLightReport {
    private val reportDays = listOf(1L, 3L, 5L, 10L)

    @JvmStatic
    fun main(args: Array<String>) {
        val reportDir = Path.of(args.firstOrNull() ?: "build/reports/examples/kitchen-light")
        Files.createDirectories(reportDir)

        val learning = KitchenLightLearning()
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
        val series = listOf(
            SampleChartRenderer.Series(world.dimmer.id, "Kitchen dimmer"),
            SampleChartRenderer.Series(world.light.id, "Kitchen light"),
        )

        val finalDayStart = Instant.EPOCH.plus(Duration.ofDays(9))
        val finalDayEnd = Instant.EPOCH.plus(duration)
        val finalDaySamples = learning.sampleStore.get(
            finalDayStart,
            finalDayEnd.plusNanos(1),
        )

        renderer.render(
            title = "Kitchen dimmer → light — day 10",
            yAxisTitle = "Level",
            samples = finalDaySamples,
            series = series,
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
                title = "Kitchen expectations — day $day",
                yAxisTitle = "Level",
                samples = samples,
                series = series,
                overlays = expectationOverlays(
                    expectations = expectations,
                    reportStart = reportStart,
                    reportEnd = reportEnd,
                ),
                output = reportDir.resolve("expectations-day-$day.png"),
            )
        }

        val expectations = learning.trainer.expectations
        val resolved = expectations.count { it.result != null }
        val fulfilled = expectations.count { it.result?.value == "Fulfilled" }
        val violated = expectations.count { it.result?.value == "Violated" }
        val bySignal = expectations.groupingBy { it.signal.id.value }.eachCount().toSortedMap()
        val resultsBySignal = expectations
            .filter { it.result != null }
            .groupBy { it.signal.id.value }
            .toSortedMap()
            .mapValues { (_, signalExpectations) ->
                val signalFulfilled = signalExpectations.count { it.result?.value == "Fulfilled" }
                val signalViolated = signalExpectations.count { it.result?.value == "Violated" }
                "$signalFulfilled fulfilled / $signalViolated violated"
            }
        val routes = learning.graph.predictionExecutions().map { it.prediction.signal.id.value }.distinct().sorted()
        val objectiveSummary = listOf(world.dimmer.id, world.light.id)
            .joinToString(separator = "\n") { signalId ->
                "- `${signalId.value}`: ${objectiveWeights(learning, signalId)}"
            }

        Files.writeString(
            reportDir.resolve("summary.md"),
            """
            ## Kitchen dimmer/light example

            Ten simulated days with two previously unknown numeric signals: `state.kitchen.dimmer` and
            `state.kitchen.light`. The dimmer changes only a few times during the daytime, each day's schedule is
            different, and it is forced to `0.0` between 23:00 and 06:00. The light follows the dimmer with a fixed
            **10 minute delay**. Skynvættr is not told that relationship and neither signal is configured as a target.

            Continuous and transition self-supervision run in parallel. Exact plateaus are not continuous-training
            examples, and objective weights are adapted from predictive skill relative to a no-change persistence
            baseline. Expectations remain a separate belief layer formed only from sufficiently confident/material
            model predictions.

            Learned expectation graphs are shown for days **1, 3, 5 and 10**, using only that day's observations and
            the expectations known at the end of that day. A separate graph shows the raw world state for day 10.

            Automatically discovered prediction routes: **${routes.joinToString()}**.
            Continuous training examples: **${learning.observationTrainer.trainingExampleCount}**.
            Meaningful transitions detected: **${learning.transitionTrainer.detectedTransitionCount}**.
            Transition training examples: **${learning.transitionTrainer.trainingExampleCount}**.

            Objective state after ten days:
            $objectiveSummary

            Expectations formed by signal: **${bySignal.entries.joinToString { "${it.key}: ${it.value}" }}**.
            Resolved expectations: **$resolved** (`Fulfilled`: **$fulfilled**, `Violated`: **$violated**).
            Results by signal: **${resultsBySignal.entries.joinToString { "${it.key}: ${it.value}" }}**.
            Replayable expectation experiences: **${learning.trainer.experiences.size}**.

            This lets us inspect both whether the learner increasingly exploits the fact that dimmer leads light and
            which self-supervised objective earns more influence for each signal. The daily dimmer schedule remains
            intentionally different across days apart from the overnight off period.
            """.trimIndent() + "\n",
        )
    }

    private fun objectiveWeights(
        learning: KitchenLightLearning,
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
                val suffix = expectation.result?.let { " (${it.value})" }.orEmpty()
                SampleChartRenderer.OverlaySeries(
                    label = "${expectation.signal.id.value} #${index + 1}$suffix",
                    points = listOf(
                        SampleChartRenderer.Point(start, expectation.signalInitialValue),
                        SampleChartRenderer.Point(end, expectation.value),
                    ),
                )
            }
}
