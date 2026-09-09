package no.skasti.skynvaettr.episodes

import no.skasti.skynvaettr.signals.SampleStore

/**
 * Materializes an [EpisodeDefinition] from this [SampleStore].
 *
 * This default materializer is intentionally strict: every timestamp returned for the episode
 * must contain exactly one sample for every signal in the definition. It performs no resampling,
 * interpolation, deduplication, or state inference.
 *
 * More permissive alignment strategies should be introduced explicitly rather than hidden in
 * this convenience function.
 */
fun SampleStore.get(episode: EpisodeDefinition): EpisodeData {
    val signalIds = episode.signals.map { it.id }
    val requested = signalIds.toSet()
    val samples = get(
        episode.from,
        episode.to,
        *signalIds.toTypedArray(),
    )

    val grouped = samples.groupBy { it.timestamp }

    val timestamps = grouped.keys.sorted()
    val values = timestamps.map { timestamp ->
        val samplesAtTimestamp = grouped.getValue(timestamp)
        val bySignal = samplesAtTimestamp.groupBy { it.signal.id }

        require(bySignal.keys.all { it in requested }) {
            "SampleStore returned a signal that was not requested for episode materialization"
        }

        episode.signals.map { signal ->
            val matching = bySignal[signal.id].orEmpty()
            require(matching.size == 1) {
                "Expected exactly one sample for signal ${signal.id} at $timestamp, " +
                    "but found ${matching.size}"
            }
            matching.single().value
        }
    }

    return EpisodeData(
        definition = episode,
        timestamps = timestamps,
        values = values,
    )
}
