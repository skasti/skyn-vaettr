package no.skasti.skynvaettr.episodes

import java.time.Instant

/**
 * A compact, materialized view of an [EpisodeDefinition].
 *
 * Rows are ordered in time. Each row contains exactly one value for each signal in
 * [EpisodeDefinition.signals], in the same column order. Signal identity and metadata therefore
 * live in the definition instead of being repeated in every cell.
 *
 * This type represents already aligned episode data. It deliberately does not decide how
 * asynchronous source samples should be resampled, interpolated, or otherwise aligned.
 */
class EpisodeData(
    val definition: EpisodeDefinition,
    timestamps: List<Instant>,
    values: List<List<Any?>>,
) {
    val timestamps: List<Instant> = timestamps.toList()
    val values: List<List<Any?>> = values.map { it.toList() }

    init {
        require(this.timestamps.size == this.values.size) {
            "Episode timestamps and value rows must have the same size"
        }

        this.values.forEachIndexed { index, row ->
            require(row.size == definition.signals.size) {
                "Episode row " + index + " has " + row.size +
                    " values, expected " + definition.signals.size
            }
        }

        this.timestamps.forEachIndexed { index, timestamp ->
            require(!timestamp.isBefore(definition.from) && timestamp.isBefore(definition.to)) {
                "Episode timestamp at row " + index + " is outside the episode time range [from, to)"
            }

            if (index > 0) {
                require(!timestamp.isBefore(this.timestamps[index - 1])) {
                    "Episode timestamps must be ordered"
                }
            }
        }
    }

    val rowCount: Int
        get() = timestamps.size

    val columnCount: Int
        get() = definition.signals.size
}
