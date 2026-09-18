package no.skasti.skynvaettr.signals

import no.skasti.skynvaettr.runtime.SingleSlotPort

import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.reflect.KClass
import no.skasti.skynvaettr.representation.Embedding
import no.skasti.skynvaettr.representation.Embedder
import no.skasti.skynvaettr.representation.Representation
import no.skasti.skynvaettr.representation.SignalIdentityEmbedder
import no.skasti.skynvaettr.topology.EntryPoint
import no.skasti.skynvaettr.topology.Port

/**
 * Initial sample entrypoint based on the strongest generic sensory pattern explored in playpen.
 *
 * Historical observations come from the canonical [SampleStore]. Every signal is offered the same
 * generic log-spaced history ages, and selected observations retain their actual timestamp rather
 * than treating a sample as a state that remains valid until the next update.
 *
 * Each selected observation is represented from signal identity + scalar value + relative time.
 * The entrypoint deliberately stops before learned projection and attention. The successful
 * playpen attention experiments normalized values and learned the input/key/value projections and
 * latent query from a prediction objective.
 *
 * The owning topology ingests samples before [process] is called; processing only reads history
 * and emits a representation.
 */
class SampleEntryPoint(
    private val sampleStore: MutableSampleStore,
    private val signalEmbedder: Embedder<SignalId> = SignalIdentityEmbedder(),
    private val historyAges: List<Duration> = DEFAULT_HISTORY_AGES,
    override val name: String = "SampleEntryPoint",
) : EntryPoint<Sample<*>> {

    override val inputType: KClass<Sample<*>> = Sample::class
    override val ports: List<Port<*>>
        get() = listOf(output)
    val output: Port<Representation> = SingleSlotPort("samples")

    var latestRepresentation: Representation? = null
        private set

    init {
        require(historyAges.isNotEmpty()) { "history ages must not be empty" }
        require(historyAges.all { !it.isNegative }) { "history ages must not be negative" }
        require(historyAges.any(Duration::isZero)) { "history ages must include the current observation" }
        require(maxHistoryAge > Duration.ZERO) { "history ages must include at least one positive duration" }
    }

    override fun process(items: List<Sample<*>>) {
        require(items.isNotEmpty()) { "sample entrypoint requires at least one sample" }

        val now = items.maxOf { it.timestamp }
        val history = sampleStore.get(now.minus(maxHistoryAge), now.plusNanos(1))
        val observations = selectObservations(now, history)
        require(observations.isNotEmpty()) { "sample entrypoint produced no observations" }

        val representation = Representation.from(observations.map(::encode)).also {
            latestRepresentation = it
        }

        output.emit(representation)
    }

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

    private fun relativeTime(
        timestamp: Instant,
        now: Instant,
    ): Double =
        Duration.between(now, timestamp).toMillis().toDouble() / maxHistoryAge.toMillis().toDouble()

    private fun distanceMillis(
        left: Instant,
        right: Instant,
    ): Long = abs(Duration.between(left, right).toMillis())

    private fun encode(observation: SensoryObservation): Embedding {
        val identity = signalEmbedder.embed(observation.sample.signal.id).toDoubleArray()
        val value = scalarValue(observation.sample.value)
        return Embedding.from(identity + doubleArrayOf(value, observation.relativeTime))
    }

    private fun scalarValue(value: Any?): Double =
        when (value) {
            is Number -> value.toDouble().also { require(it.isFinite()) { "numeric sample values must be finite" } }
            is Boolean -> if (value) 1.0 else 0.0
            else -> error(
                "Default sample entrypoint currently supports Number and Boolean sample values, got " +
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
        /** Generic history ages carried forward from the successful temporal relation experiments. */
        val DEFAULT_HISTORY_AGES: List<Duration> =
            listOf(5120L, 2560L, 1280L, 640L, 320L, 160L, 80L, 60L, 40L, 30L, 20L, 15L, 10L, 5L, 0L)
                .map(Duration::ofSeconds)
    }
}
