package no.skasti.skynvaettr.reporting

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import no.skasti.skynvaettr.examples.KitchenLightLearning
import no.skasti.skynvaettr.expectations.Expectation

/** Renders the dimmer/light temporal-relation example. */
object KitchenLightReport {
    @JvmStatic
    fun main(args: Array<String>) {
        val reportDir = Path.of(args.firstOrNull() ?: "build/reports/examples/kitchen-light")
        Files.createDirectories(reportDir)

        val learning = KitchenLightLearning()
        val world = learning.world
        val duration = Duration.ofDays(4)

        world.simulate(
            duration = duration,
            step = Duration.ofMinutes(5),
        ) { samples -> learning.vaettr.sense(samples) }

        val renderer = SampleChartRenderer()
        val series = listOf(
            SampleChartRenderer.Series(world.dimmer.id, "Kitchen dimmer"),
            SampleChartRenderer.Series(world.light.id, "Kitchen light"),
        )
        val samples = learning.sampleStore.get(Instant.EPOCH, Instant.EPOCH.plus(duration).plusNanos(1))

        renderer.render(
            title = "Kitchen dimmer → light — four days",
            yAxisTitle = "Level",
            samples = samples,
            series = series,
            output = reportDir.resolve("world.png"),
        )

        renderer.render(
            title = "Kitchen expectations — four days",
            yAxisTitle = "Level",
            samples = samples,
            series = series,
            overlays = expectationOverlays(
                expectations = learning.trainer.expectations,
                reportStart = Instant.EPOCH,
                reportEnd = Instant.EPOCH.plus(duration),
            ),
            output = reportDir.resolve("expectations.png"),
        )

        val expectations = learning.trainer.expectations
        val resolved = expectations.count { it.result != null }
        val fulfilled = expectations.count { it.result?.value == "Fulfilled" }
        val violated = expectations.count { it.result?.value == "Violated" }
        val bySignal = expectations.groupingBy { it.signal.id.value }.eachCount().toSortedMap()
        val routes = learning.graph.predictionExecutions().map { it.prediction.signal.id.value }.distinct().sorted()

        Files.writeString(
            reportDir.resolve("summary.md"),
            """
            ## Kitchen dimmer/light example

            Four simulated days with two previously unknown numeric signals: `state.kitchen.dimmer` and
            `state.kitchen.light`. The dimmer changes only a few times during the daytime, each day's schedule is
            different, and it is forced to `0.0` between 23:00 and 06:00. The light follows the dimmer with a fixed
            **10 minute delay**. Skynvættr is not told that relationship and neither signal is configured as a target.

            Automatically discovered prediction routes: **${routes.joinToString()}**.
            Expectations formed by signal: **${bySignal.entries.joinToString { "${it.key}: ${it.value}" }}**.
            Resolved expectations: **$resolved** (`Fulfilled`: **$fulfilled**, `Violated`: **$violated**).
            Replayable experiences: **${learning.trainer.experiences.size}**.

            The first graph shows only the world state. The second overlays all expectations formed during the same
            four-day run, allowing us to inspect whether the generic learner starts exploiting the leading dimmer
            signal rather than relying only on repeated daily timing.
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
