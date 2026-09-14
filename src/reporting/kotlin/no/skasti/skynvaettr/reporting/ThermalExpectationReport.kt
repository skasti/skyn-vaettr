package no.skasti.skynvaettr.reporting

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import no.skasti.skynvaettr.Vaettr
import no.skasti.skynvaettr.examples.ThermalExpectationScenario
import no.skasti.skynvaettr.expectations.Expectation
import no.skasti.skynvaettr.expectations.ExpectationResult
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
        val reportEnd = Instant.EPOCH.plus(duration)

        world.simulate(
            duration = duration,
            step = Duration.ofMinutes(5),
        ) { samples ->
            vaettr.sense(samples)
        }

        val samples = vaettr.sampleStore.get(
            Instant.EPOCH,
            reportEnd.plusNanos(1),
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

        val expectationSeries = illustrativeExpectations(samples, world, reportEnd)
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

            Each expectation series begins at `formedAt` and remains visible while the expectation is active. A result
            closes the series at `ExpectationResult.timestamp`; an expectation without a result remains visible through
            the end of the report window. Completed series include the result value in the legend, for example
            `Expectation 2 (Abandoned)`.

            The x-axis means **when the expectation was held**, not a fixed target timestamp or prediction horizon. Each
            expectation keeps its original `signalInitialValue` and `expectationInitialValue` while its current `value`
            may be refined.
            """.trimIndent() + "\n",
        )
    }

    private fun illustrativeExpectations(
        samples: List<Sample<*>>,
        world: ThermalExpectationScenario,
        reportEnd: Instant,
    ): List<SampleChartRenderer.OverlaySeries> {
        data class Refinement(
            val afterFormation: Duration,
            val offset: Double,
        )

        data class Example(
            val formedAt: Duration,
            val initialOffset: Double,
            val refinements: List<Refinement>,
            val result: Pair<Duration, String>? = null,
        )

        val examples = listOf(
            Example(
                formedAt = Duration.ofHours(4),
                initialOffset = 1.6,
                refinements = listOf(
                    Refinement(Duration.ofMinutes(45), 1.9),
                    Refinement(Duration.ofMinutes(90), 2.1),
                ),
                result = Duration.ofHours(3) to "Fulfilled",
            ),
            Example(
                formedAt = Duration.ofHours(11),
                initialOffset = 1.0,
                refinements = listOf(
                    Refinement(Duration.ofMinutes(50), 0.8),
                    Refinement(Duration.ofMinutes(100), 0.5),
                ),
                result = Duration.ofHours(2) to "Abandoned",
            ),
            Example(
                formedAt = Duration.ofHours(18),
                initialOffset = -1.4,
                refinements = listOf(
                    Refinement(Duration.ofMinutes(40), -1.7),
                    Refinement(Duration.ofMinutes(80), -1.9),
                ),
            ),
        )

        return examples.mapIndexed { index, example ->
            val formedAt = Instant.EPOCH.plus(example.formedAt)
            val signalInitialValue = numericValueAt(samples, world.indoorTemperature.id.value, formedAt)
            val initialExpectedValue = signalInitialValue + example.initialOffset
            val result = example.result?.let { (afterFormation, value) ->
                ExpectationResult(
                    value = value,
                    timestamp = formedAt.plus(afterFormation),
                )
            }
            val initial = Expectation(
                signal = world.indoorTemperature,
                signalInitialValue = signalInitialValue,
                value = initialExpectedValue,
                formedAt = formedAt,
                confidence = 0.65 + index * 0.1,
                result = result,
            )

            var currentValue = initial.value
            val points = buildList {
                add(SampleChartRenderer.Point(initial.formedAt, currentValue))

                example.refinements.forEach { refinement ->
                    val timestamp = initial.formedAt.plus(refinement.afterFormation)
                    if (initial.result == null || !timestamp.isAfter(initial.result.timestamp)) {
                        currentValue = signalInitialValue + refinement.offset
                        add(SampleChartRenderer.Point(timestamp, currentValue))
                    }
                }

                val activeUntil = initial.result?.timestamp ?: reportEnd
                if (last().timestamp != activeUntil) {
                    add(SampleChartRenderer.Point(activeUntil, currentValue))
                }
            }

            val resultSuffix = initial.result?.let { " (${it.value})" }.orEmpty()
            SampleChartRenderer.OverlaySeries(
                label = "Expectation ${index + 1}$resultSuffix",
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
