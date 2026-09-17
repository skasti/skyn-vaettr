package no.skasti.skynvaettr.reporting

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import no.skasti.skynvaettr.Vaettr
import no.skasti.skynvaettr.runtime.DefaultTopology
import no.skasti.skynvaettr.examples.ThermalExpectationScenario
import no.skasti.skynvaettr.expectations.Expectation
import no.skasti.skynvaettr.expectations.ExpectationResult
import no.skasti.skynvaettr.signals.SampleEntryPoint
import no.skasti.skynvaettr.signals.Sample

/** Renders the inspectable report for the minimal thermal expectation example. */
object ThermalExpectationReport {
    @JvmStatic
    fun main(args: Array<String>) {
        val reportDir = Path.of(args.firstOrNull() ?: "build/reports/examples/thermal-expectation")
        Files.createDirectories(reportDir)

        val world = ThermalExpectationScenario()
        val sampleEntryPoint = SampleEntryPoint()
        val topology = DefaultTopology(world, listOf(sampleEntryPoint))
        val vaettr = Vaettr(world, topology)
        val duration = Duration.ofDays(1)
        val reportStart = Instant.EPOCH
        val reportEnd = reportStart.plus(duration)

        world.simulate(
            vaettr = vaettr,
            duration = duration,
            step = Duration.ofMinutes(5),
        )

        val samples = sampleEntryPoint.sampleStore.get(reportStart, reportEnd.plusNanos(1))
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

        val expectations = illustrativeExpectations(samples, world)
        renderer.render(
            title = "Thermal expectation example — illustrative expectations",
            yAxisTitle = "Temperature (°C)",
            samples = samples,
            series = worldSeries,
            overlays = ExpectationChartSupport.overlays(
                expectations = expectations,
                windowStart = reportStart,
                windowEnd = reportEnd,
            ),
            output = reportDir.resolve("expectations.png"),
        )

        TopologyRenderer().render(topology, reportDir)

        Files.writeString(
            reportDir.resolve("summary.md"),
            """
            ## Thermal expectation example

            One simulated day using only `sensor.outdoor.temperature` and `sensor.indoor.temperature` as observations.
            Outdoor temperature follows a 10–25 °C daily sine wave. Indoor temperature responds with thermal inertia,
            retaining 90% of the remaining outdoor/indoor delta after one hour by default.

            `day.png` shows only world observations. `expectations.png` adds three **illustrative** expectations for
            `sensor.indoor.temperature`. They are not produced by a learned model.

            Expectation rendering is model-independent: each line starts at the signal's observed `signalInitialValue`
            when the expectation is formed and progresses toward its current expected `value`. A resolved expectation ends
            at `ExpectationResult.timestamp`; an unresolved expectation remains visible through the report window. Resolved
            series include the result in the legend, for example `Expectation 2 (Abandoned)`.

            If an expectation crosses the visible chart boundary, the line is clipped and interpolated at that boundary.
            The x-axis therefore represents the period during which the belief is held, not a fixed prediction horizon.
            """.trimIndent() + "\n",
        )
    }

    private fun illustrativeExpectations(
        samples: List<Sample<*>>,
        world: ThermalExpectationScenario,
    ): List<Expectation<Double>> {
        data class Example(
            val formedAt: Duration,
            val offset: Double,
            val result: Pair<Duration, String>? = null,
        )

        return listOf(
            Example(
                formedAt = Duration.ofHours(4),
                offset = 2.1,
                result = Duration.ofHours(3) to "Fulfilled",
            ),
            Example(
                formedAt = Duration.ofHours(11),
                offset = 0.5,
                result = Duration.ofHours(2) to "Abandoned",
            ),
            Example(
                formedAt = Duration.ofHours(18),
                offset = -1.9,
            ),
        ).mapIndexed { index, example ->
            val formedAt = Instant.EPOCH.plus(example.formedAt)
            val signalInitialValue = numericValueAt(samples, world.indoorTemperature.id.value, formedAt)
            Expectation(
                signal = world.indoorTemperature,
                signalInitialValue = signalInitialValue,
                value = signalInitialValue + example.offset,
                formedAt = formedAt,
                confidence = 0.65 + index * 0.1,
                result = example.result?.let { (afterFormation, value) ->
                    ExpectationResult(
                        value = value,
                        timestamp = formedAt.plus(afterFormation),
                    )
                },
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
