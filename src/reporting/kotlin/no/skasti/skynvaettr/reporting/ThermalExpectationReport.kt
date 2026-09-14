package no.skasti.skynvaettr.reporting

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import no.skasti.skynvaettr.Vaettr
import no.skasti.skynvaettr.examples.ThermalExpectationScenario

/** Renders the inspectable report for the minimal thermal expectation example. */
object ThermalExpectationReport {
    @JvmStatic
    fun main(args: Array<String>) {
        val reportDir = Path.of(args.firstOrNull() ?: "build/reports/examples/thermal-expectation")
        Files.createDirectories(reportDir)

        val vaettr = Vaettr()
        val world = ThermalExpectationScenario()
        val duration = Duration.ofDays(1)

        world.simulate(
            duration = duration,
            step = Duration.ofMinutes(5),
        ) { samples ->
            vaettr.sense(samples)
        }

        val samples = vaettr.sampleStore.get(
            Instant.EPOCH,
            Instant.EPOCH.plus(duration).plusNanos(1),
        )

        SampleChartRenderer().render(
            title = "Thermal expectation example — one simulated day",
            yAxisTitle = "Temperature (°C)",
            samples = samples,
            series = listOf(
                SampleChartRenderer.Series(world.outdoorTemperature.id, "Outdoor temperature"),
                SampleChartRenderer.Series(world.indoorTemperature.id, "Indoor temperature"),
            ),
            output = reportDir.resolve("day.png"),
        )

        Files.writeString(
            reportDir.resolve("summary.md"),
            """
            ## Thermal expectation example

            One simulated day using only `sensor.outdoor.temperature` and `sensor.indoor.temperature` as observations.
            Outdoor temperature follows a 10–25 °C daily sine wave. Indoor temperature responds with thermal inertia,
            retaining 90% of the remaining outdoor/indoor delta after one hour by default.

            The graph is intentionally the **world data only**. A later learned model should add a running expectation of
            future `sensor.indoor.temperature` to this same report so prediction quality can be inspected against reality.
            """.trimIndent() + "\n",
        )
    }
}
