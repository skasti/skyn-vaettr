package no.skasti.skynvaettr.runtime

import java.time.Duration
import java.time.Instant
import no.skasti.skynvaettr.attention.Attention
import no.skasti.skynvaettr.attention.ScaledDotProductAttention
import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Embedder
import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.representation.SignalIdentityEmbedder
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.SignalId

/**
 * Initial default sensing graph based on the strongest generic sensory pattern explored in playpen.
 *
 * The graph keeps timestamped sensory history, selects the same generic log-spaced history ages for
 * every signal, represents each observation from signal identity + scalar value + relative time,
 * and contextualizes the resulting positions with a replaceable attention implementation.
 *
 * It deliberately stops at a generic [Representation]. Prediction heads, objectives, memory,
 * effectors, training and higher-level processing belong to later graph components.
 */
class SensingProcessingGraph(
    private val signalEmbedder: Embedder<SignalId> = SignalIdentityEmbedder(),
    private val attention: Attention = ScaledDotProductAttention(),
    private val historyAges: List<Duration> = DEFAULT_HISTORY_AGES,
) : ProcessingGraph {
    private val history = mutableMapOf<SignalId, MutableList<Sample<*>>>()

    var latestRepresentation: Representation? = null
        private set

    init {
        require(historyAges.isNotEmpty()) { "history ages must not be empty" }
        require(historyAges.all { !it.isNegative }) { "history ages must not be negative" }
        require(historyAges.any(Duration::isZero)) { "history ages must include the current observation" }
    }

    override fun sense(samples: List<Sample<*>>) {
        if (samples.isEmpty()) return

        samples.forEach { sample ->
            history.getOrPut(sample.signal.id) { mutableListOf() }.add(sample)
        }

        val now = samples.maxOf { it.timestamp }
        prune(now)
        val observations = selectObservations(now)
        if (observations.isEmpty()) return

        val raw = Representation.from(observations.map(::encode))
        latestRepresentation = attention.selfAttention(raw).output
    }

    private fun selectObservations(now: Instant): List<SensoryObservation> = buildList {
        history.entries.sortedBy { it.key.value }.forEach { (_, samples) ->
            historyAges.forEach { age ->
                val target = now.minus(age)
                val selected = samples.lastOrNull { !it.timestamp.isAfter(target) } ?: return@forEach
                add(
                    SensoryObservation(
                        sample = selected,
                        relativeTime = -age.toMillis().toDouble() / maxHistoryAge.toMillis().toDouble(),
                    ),
                )
            }
        }
    }

    private fun encode(observation: SensoryObservation): Embedding {
        val identity = signalEmbedder.embed(observation.sample.signal.id).toDoubleArray()
        val value = scalarValue(observation.sample.value)
        return Embedding.from(identity + doubleArrayOf(value, observation.relativeTime))
    }

    private fun scalarValue(value: Any?): Double =
        when (value) {
            is Number -> value.toDouble().also { require(it.isFinite()) { "numeric sample values must be finite" } }
            is Boolean -> if (value) 1.0 else 0.0
            else -> error("Default sensing graph currently supports Number and Boolean sample values, got ${value?.let { it::class.simpleName } ?: "null"}")
        }

    private fun prune(now: Instant) {
        val cutoff = now.minus(maxHistoryAge)
        history.values.forEach { samples ->
            val firstRetained = samples.indexOfFirst { !it.timestamp.isBefore(cutoff) }
            if (firstRetained > 0) {
                // Keep one sample before the horizon so nearest-at-or-before lookup can still resolve
                // the oldest requested history position.
                samples.subList(0, firstRetained - 1).clear()
            }
        }
    }

    private val maxHistoryAge: Duration
        get() = historyAges.maxOrNull() ?: Duration.ZERO

    private data class SensoryObservation(
        val sample: Sample<*>,
        val relativeTime: Double,
    )

    companion object {
        /** Generic history ages carried forward from the successful temporal relation experiments. */
        val DEFAULT_HISTORY_AGES: List<Duration> =
            listOf(5120L, 2560L, 1280L, 640L, 320L, 160L, 80L, 60L, 40L, 30L, 20L, 15L, 10L, 5L, 0L)
                .map(Duration::ofSeconds)
    }
}
