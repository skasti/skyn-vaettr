package no.skasti.skynvaettr.reporting

import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import no.skasti.skynvaettr.signals.SignalId
import no.skasti.skynvaettr.training.TransitionPredictionRecord

object TransitionPredictionReportSupport {
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

    /**
     * Aggregate self-attention from query signal to key signal.
     *
     * Each self-attention row already sums to one. We first sum a row's weights by key signal and
     * then average those rows by query signal. The resulting heatmap therefore answers e.g.
     * "when light is the query, how much attention does it assign to dimmer positions?" rather than
     * merely reflecting how many history positions happened to exist in an age bucket.
     */
    fun renderAttentionHeatmap(
        title: String,
        records: List<TransitionPredictionRecord>,
        signals: List<SignalId>,
        output: Path,
    ) {
        val matrix = signals.map { DoubleArray(signals.size) }
        val queryCounts = IntArray(signals.size)

        records.forEach { record ->
            record.attention
                .groupBy { it.queryIndex }
                .values
                .forEach { queryRow ->
                    val querySignal = queryRow.firstOrNull()?.querySignalId ?: return@forEach
                    val row = signals.indexOf(querySignal)
                    if (row < 0) return@forEach
                    queryCounts[row]++

                    queryRow.forEach { attribution ->
                        val column = signals.indexOf(attribution.keySignalId)
                        if (column >= 0) matrix[row][column] += attribution.weight
                    }
                }
        }

        matrix.forEachIndexed { row, values ->
            val count = queryCounts[row]
            if (count > 0) values.indices.forEach { column -> values[column] /= count.toDouble() }
        }

        HeatmapRenderer().render(
            title = title,
            rowLabels = signals.map { "query: ${it.value}" },
            columnLabels = signals.map { "attends to: ${it.value}" },
            values = matrix,
            output = output,
            valueFormatter = { value -> "%.1f%%".format(value * 100.0) },
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
}
