package no.skasti.skynvaettr.environment

import java.time.Instant
import kotlin.reflect.KClass
import no.skasti.skynvaettr.signals.InMemorySampleStore
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.SampleStore
import no.skasti.skynvaettr.signals.SignalId

/** Simple process-local environment suitable for experiments and tests. */
open class InMemoryEnvironment : Environment, SampleStore {
    private val items = mutableListOf<Any>()
    private val consumedCounts = mutableMapOf<KClass<*>, Int>()
    private val history = mutableListOf<ProcessingInput>()
    private var nextSequence: ULong = 1uL

    /** Read-only view of the samples ingested by this environment. */
    val sampleStore: SampleStore
        get() = this

    fun append(items: List<*>) {
        val nonNullItems = items.map { item ->
            requireNotNull(item) { "environment items must not be null" }
        }
        this.items.addAll(nonNullItems)
    }

    fun append(item: Any) = append(listOf(item))

    override fun get(
        after: Instant,
        before: Instant,
        vararg signals: SignalId,
    ): List<Sample<*>> {
        return items.filterIsInstance<Sample<*>>()
            .filter { sample ->
                sample.timestamp > after && sample.timestamp < before &&
                    (signals.isEmpty() || sample.signal.id in signals)
            }
    }

    override fun getNew(types: List<KClass<*>>): ProcessingInput? {
        val values = types.distinct().associateWith { type ->
            val objectType = type.javaObjectType
            val matching = items.filter { objectType.isInstance(it) }
            val consumed = consumedCounts[type] ?: 0
            require(consumed <= matching.size) {
                "environment items cannot be removed while consumption is tracked"
            }
            consumedCounts[type] = matching.size
            matching.drop(consumed)
        }

        if (values.values.all(List<Any>::isEmpty)) return null

        val input = ProcessingInput(nextSequence++, values)
        history += input
        return input
    }

    override fun getHistory(
        types: List<KClass<*>>,
        from: ULong,
        to: ULong,
    ): List<ProcessingInput> {
        require(from <= to) { "history start sequence must not be after its end sequence" }
        require(from >= 1uL) { "processing input sequences start at 1" }
        val requested = types.distinct().toSet()

        val historySize = history.size.toULong()
        if (from > historySize) return emptyList()

        val startIndex = (from - 1uL).toInt()
        val endIndex = minOf(to - 1uL, historySize).toInt()

        return history.subList(startIndex, endIndex)
            .mapNotNull { input ->
                val values = input.values.filterKeys { it in requested }
                if (values.values.all(List<Any>::isEmpty)) null
                else input.copy(values = values)
            }
    }
}
