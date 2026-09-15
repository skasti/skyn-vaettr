package no.skasti.skynvaettr.runtime

import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Embedder
import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.representation.SignalIdentityEmbedder
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.SampleStore
import no.skasti.skynvaettr.signals.SignalId

/** Symbolic provenance kept beside latent positions for diagnostics only. */
data class SensoryPosition(
    val signalId: SignalId,
    val timestamp: Instant,
    val relativeTime: Double,
)

/** One encoded sensory-history frame and the provenance of its positions. */
data class SensoryFrame(
    val representation: Representation,
    val positions: List<SensoryPosition>,
)

/**
 * Converts sampled history into the generic [Representation] consumed by learned models.
 *
 * Signal identity encoding delegates to [SignalIdentityEmbedder], which performs signal-id
 * tokenization and token encoding. Each final sensory position is therefore:
 *
 * `[signal identity embedding..., scalar value, actual relative time]`.
 *
 * Position metadata is deliberately kept outside [Representation]; models consume only the encoded
 * vectors while reports can still label attention weights with the originating signal and time.
 */
class SensoryRepresentationEncoder(
    private val sampleStore: SampleStore,
    private val signalEmbedder: Embedder<SignalId> = SignalIdentityEmbedder(),
    private val historyAges: List<Duration> = DEFAULT_HISTORY_AGES,
) {
    init {
        require(historyAges.isNotEmpty()) { "history ages must not be empty" }
        require(historyAges.all { !it.isNegative }) { "history ages must not be negative" }
        require(historyAges.any(Duration::isZero)) { "history ages must include the current observation" }
        require(maxHistoryAge > Duration.ZERO) { "history ages must include at least one positive duration" }
    }

    fun frame(now: Instant): SensoryFrame? {
        val history = sampleStore.get(now.minus(maxHistoryAge), now.plusNanos(1))
        val observations = selectObservations(now, history)
        if (observations.isEmpty()) return null

        return SensoryFrame(
            representation = Representation.from(observations.map { encode(it.sample, it.relativeTime) }),
            positions = observations.map { observation ->
                SensoryPosition(
                    signalId = observation.sample.signal.id,
                    timestamp = observation.sample.timestamp,
                    relativeTime = observation.relativeTime,
                )
            },
        )
    }

    /** Encodes an exact event/sample using the same vector layout as history positions. */
    fun encodeEvent(sample: Sample<*>, now: Instant): Embedding =
        encode(sample, relativeTime(sample.timestamp, now))

    private fun selectObservations(
        now: Instant,
        history: List<Sample<*>>,
    ): List<SensoryObservation> = buildList {
        history
            .groupBy { it.signal.id }
            .toSortedMap(compareBy(SignalId::value))
            .forEach { (_, signalSamples) ->
                val selected = linkedSetOf<Sample<*>>()
                historyAges.forEach { age ->
                    val target = now.minus(age)
                    signalSamples.minByOrNull { sample -> distanceMillis(sample.timestamp, target) }?.let(selected::add)
                }
                selected.sortedBy { it.timestamp }.forEach { sample ->
                    add(
                        SensoryObservation(
                            sample = sample,
                            relativeTime = relativeTime(sample.timestamp, now),
                        ),
                    )
                }
            }
    }

    private fun encode(sample: Sample<*>, relativeTime: Double): Embedding {
        val identity = signalEmbedder.embed(sample.signal.id).toDoubleArray()
        return Embedding.from(identity + doubleArrayOf(scalarValue(sample.value), relativeTime))
    }

    private fun relativeTime(timestamp: Instant, now: Instant): Double =
        Duration.between(now, timestamp).toMillis().toDouble() / maxHistoryAge.toMillis().toDouble()

    private fun distanceMillis(left: Instant, right: Instant): Long =
        abs(Duration.between(left, right).toMillis())

    private fun scalarValue(value: Any?): Double =
        when (value) {
            is Number -> value.toDouble().also { require(it.isFinite()) { "numeric sample values must be finite" } }
            is Boolean -> if (value) 1.0 else 0.0
            else -> error(
                "Default sensory encoding currently supports Number and Boolean sample values, got " +
                    (value?.let { it::class.simpleName } ?: "null"),
            )
        }

    private val maxHistoryAge: Duration
        get() = historyAges.maxOrNull() ?: Duration.ZERO

    private data class SensoryObservation(
        val sample: Sample<*>,
        val relativeTime: Double,
    )

    companion object {
        val DEFAULT_HISTORY_AGES: List<Duration> =
            listOf(5120L, 2560L, 1280L, 640L, 320L, 160L, 80L, 60L, 40L, 30L, 20L, 15L, 10L, 5L, 0L)
                .map(Duration::ofSeconds)
    }
}
