package no.skasti.skynvaettr.reporting

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import no.skasti.skynvaettr.Vaettr
import no.skasti.skynvaettr.runtime.DefaultTopology
import no.skasti.skynvaettr.examples.KitchenLightScenario
import no.skasti.skynvaettr.runtime.SampleEntryPoint

/** Renders the kitchen world itself, without involving models, predictions or expectations. */
object KitchenLightScenarioReport {
    @JvmStatic
    fun main(args: Array<String>) {
        val reportDir = Path.of(args.firstOrNull() ?: "build/reports/examples/kitchen-light")
        Files.createDirectories(reportDir)

        val world = KitchenLightScenario()
        val sampleEntryPoint = SampleEntryPoint()
        val vaettr = Vaettr(world, DefaultTopology(world, listOf(sampleEntryPoint)))
        val duration = Duration.ofDays(10)
        world.simulate(
            vaettr = vaettr,
            duration = duration,
            step = Duration.ofMinutes(5),
        )
        val samples = sampleEntryPoint.sampleStore.get(Instant.EPOCH, Instant.EPOCH.plusNanos(duration.toNanos() + 1))

        val finalDayStart = Instant.EPOCH.plus(Duration.ofDays(9))
        val finalDayEnd = Instant.EPOCH.plus(duration)
        val finalDaySamples = samples.filter { !it.timestamp.isBefore(finalDayStart) && it.timestamp <= finalDayEnd }

        SampleChartRenderer().render(
            title = "Kitchen dimmer → light — day 10",
            yAxisTitle = "Level",
            samples = finalDaySamples,
            series = listOf(
                SampleChartRenderer.Series(world.dimmer.id, "Kitchen dimmer"),
                SampleChartRenderer.Series(world.light.id, "Kitchen light"),
            ),
            output = reportDir.resolve("day.png"),
        )

        Files.writeString(
            reportDir.resolve("summary.md"),
            """
            ## Kitchen dimmer/light scenario

            This report renders only the deterministic example world. It does **not** create or train any model,
            Prediction or Expectation.

            `state.kitchen.dimmer` changes a few times during the day, with a different deterministic schedule each day.
            `state.kitchen.light` follows the dimmer with a fixed **10 minute delay**. The scenario is intended as a small,
            inspectable environment for future experiments where the learner is not told that relationship.
            """.trimIndent() + "\n",
        )
    }
}
