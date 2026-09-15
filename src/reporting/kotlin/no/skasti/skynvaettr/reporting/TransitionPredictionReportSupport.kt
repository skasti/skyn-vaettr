package no.skasti.skynvaettr.reporting

import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import no.skasti.skynvaettr.signals.SignalId
import no.skasti.skynvaettr.training.TransitionPredictionRecord

object TransitionPredictionReportSupport {
    /** Reporting-only buckets; the model receives actual relative times, not these bucket boundaries. */
    private val ageBuckets = listOf(
        AgeBucket("0–5m", Duration.ZERO, Duration.ofMinutes(5)),
        AgeBucket("5–15m", Duration.ofMinutes(5), Duration.ofMinutes(15)),
        AgeBucket("15–30m", Duration.ofMinutes(15), Duration.ofMinutes(30)),
        AgeBucket("30–60m", Duration.ofMinutes(30), Duration.ofMinutes(60)),
        AgeBucket("60–86m", Duration.ofMinutes(60), Duration.ofMinutes(86)),
    )

    data class DayMetrics(
        val records: Int,
        val signalAccuracy: Double,
        val valueMae: Double,
    )

    fun recordsForDay(
        records: List<TransitionPredictionRecord>,
        day: Long,
    ): List<TransitionPredictionRecord> {
        val start = Instant.EPOCH.plus(Duration.ofDays(day - 1))
        val end = Instant.EPOCH.plus(Duration.ofDays(day))
        return records.filter { !it.resolvedAt.isBefore(start) && it.resolvedAt.isBefore(end) }
    }

    fun metrics(records: List<TransitionPredictionRecord>): DayMetrics {
        if (records.isEmpty()) return DayMetrics(0, 0.0, Double.NaN)
        return DayMetrics(
            records = records.size,
            signalAccuracy = records.count(TransitionPredictionRecord::signalCorrect).toDouble() / records.size,
            valueMae = records.sumOf { kotlin.math.abs(it.prediction.value - it.actualValue) } / records.size,
        )
    }

    fun renderAttentionHeatmap(
        title: String,
        records: List<TransitionPredictionRecord>,
        signals: List<SignalId>,
        output: Path,
    ) {
        val matrix = signals.map { DoubleArray(ageBuckets.size) }
        if (records.isNotEmpty()) {
            records.forEach { record ->
                record.attention.forEach { attribution ->
                    val row = signals.indexOf(attribution.signalId)
                    if (row < 0) return@forEach
                    val column = ageBuckets.indexOfFirst { it.contains(attribution.age) }
                    if (column >= 0) matrix[row][column] += attribution.weight
                }
            }
            matrix.forEach { row ->
                row.indices.forEach { column -> row[column] /= records.size.toDouble() }
            }
        }

        HeatmapRenderer().render(
            title = title,
            rowLabels = signals.map { it.value },
            columnLabels = ageBuckets.map { it.label },
            values = matrix,
            output = output,
            valueFormatter = { value -> "%.3f".format(value) },
        )
    }

    fun renderTargetSignalHeatmap(
        title: String,
        records: List<TransitionPredictionRecord>,
        signals: List<SignalId>,
        output: Path,
    ) {
        val matrix = signals.map { DoubleArray(signals.size) }
        records.forEach { record ->
            val actual = signals.indexOf(record.actualSignal)
            val predicted = signals.indexOf(record.prediction.signal.id)
            if (actual >= 0 && predicted >= 0) matrix[actual][predicted]++
        }

        matrix.forEach { row ->
            val total = row.sum()
            if (total > 0.0) row.indices.forEach { column -> row[column] /= total }
        }

        HeatmapRenderer().render(
            title = title,
            rowLabels = signals.map { "actual: ${it.value}" },
            columnLabels = signals.map { "predicted: ${it.value}" },
            values = matrix,
            output = output,
            valueFormatter = { value -> "%.0f%%".format(value * 100.0) },
        )
    }

    private data class AgeBucket(
        val label: String,
        val fromInclusive: Duration,
        val toExclusive: Duration,
    ) {
        fun contains(age: Duration): Boolean = age >= fromInclusive && age < toExclusive
    }
}
