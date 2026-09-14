package no.skasti.skynvaettr.reporting

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import no.skasti.skynvaettr.Vaettr
import no.skasti.skynvaettr.examples.ThermalExpectationScenario
import no.skasti.skynvaettr.expectations.Expectation
import no.skasti.skynvaettr.signals.Sample

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

        val renderer = SampleChartRenderer()
        val worldSeries = listOf(
            SampleChartRenderer.Series(world.outdoorTemperature.id, "Outdoor temperature"),
            SampleChartRenderer.Series(world.indoorTemperature.id, "Indoor temperature"),
        )

        renderer.render(
            title = "Thermal expectation example — one simulated day",
            yAxisTitle = "Temperature (°C)",
            samples = samples,
            series = worldSeries,
            output = reportDir.resolve("day.png"),
        )

        val expectationSeries = illustrativeExpectations(samples, world)
        renderer.render(
            title = "Thermal expectation example — illustrative expectations",
            yAxisTitle = "Temperature (°C)",
            samples = samples,
            series = worldSeries,
            overlays = expectationSeries,
            output = reportDir.resolve("expectations.png"),
        )

        Files.writeString(
            reportDir.resolve("summary.md"),
            """
            ## Thermal expectation example

            One simulated day using only `sensor.outdoor.temperature` and `sensor.indoor.temperature` as observations.
            Outdoor temperature follows a 10–25 °C daily sine wave. Indoor temperature responds with thermal inertia,
            retaining 90% of the remaining outdoor/indoor delta after one hour by default.

            `day.png` shows only world observations. `expectations.png` adds three **illustrative** expectations for
            `sensor.indoor.temperature` as separate series. They are not produced by a learned model; they exist to make
            the expectation representation inspectable while the model/gate behavior is still experimental.

            Each expectation series is plotted at the time the belief exists or is refined. The x-axis therefore means
            **when the expectation was held**, not a fixed target timestamp or prediction horizon. Each expectation keeps
            its original `signalInitialValue` and `expectationInitialValue` while its current `value` is refined.
            """.trimIndent() + "\n",
        )
    }

    private fun illustrativeExpectations(
        samples: List<Sample<*>>,
        world: ThermalExpectationScenario,
    ): List<SampleChartRenderer.OverlaySeries> {
        data class Example(
            val formedAt: Duration,
            val initialOffset: Double,
            val refinements: List<Pair<Duration, Double>>,
        )

        val examples = listOf(
            Example(
                formedAt = Duration.ofHours(4),
                initialOffset = 1.6,
                refinements = listOf(
                    Duration.ofMinutes(45) to 1.9,
                    Duration.ofMinutes(90) to 2.1,
                ),
            ),
            Example(
                formedAt = Duration.ofHours(11),
                initialOffset = 1.0,
                refinements = listOf(
                    Duration.ofMinutes(50) to 0.8,
                    Duration.ofMinutes(100) to 0.5,
                ),
            ),
            Example(
                formedAt = Duration.ofHours(18),
                initialOffset = -1.4,
                refinements = listOf(
                    Duration.ofMinutes(40) to -1.7,
                    Duration.ofMinutes(80) to -1.9,
                ),
            ),
        )

        return examples.mapIndexed { index, example ->
            val formedAt = Instant.EPOCH.plus(example.formedAt)
            val signalInitialValue = numericValueAt(samples, world.indoorTemperature.id.value, formedAt)
            val initialExpectedValue = signalInitialValue + example.initialOffset
            val initial = Expectation(
                signal = world.indoorTemperature,
                signalInitialValue = signalInitialValue,
                value = initialExpectedValue,
                formedAt = formedAt,
                confidence = 0.65 + index * 0.1,
            )

            val points = buildList {
                add(SampleChartRenderer.Point(initial.formedAt, initial.value))
                example.refinements.forEach { (afterFormation, offset) ->
                    val refined = initial.copy(value = signalInitialValue + offset)
                    add(
                        SampleChartRenderer.Point(
                            timestamp = initial.formedAt.plus(afterFormation),
                            value = refined.value,
                        ),
                    )
                }
            }

            SampleChartRenderer.OverlaySeries(
                label = "Expectation ${index + 1}",
                points = points,
            )
        }
    }

    private fun numericValueAt(
        samples: List<Sample<*>>,
        signalId: String,
        timestamp: Instant,
    ): Double = samples
        .asSequence()
        .filter { it.signal.id.value == signalId && it.timestamp == timestamp }
        .mapNotNull { (it.value as? Number)?.toDouble() }
        .firstOrNull()
        ?: error("No numeric sample for $signalId at $timestamp")
}
