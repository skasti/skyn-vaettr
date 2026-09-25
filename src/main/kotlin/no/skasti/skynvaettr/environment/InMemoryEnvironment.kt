package no.skasti.skynvaettr.environment

import java.time.Instant
import kotlin.reflect.KClass
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.SampleStore
import no.skasti.skynvaettr.signals.SignalId

/** Simple process-local environment suitable for experiments and tests. */
open class InMemoryEnvironment : Environment, SampleStore {
    private val items = mutableListOf<Any>()
    private val consumedCounts = mutableMapOf<KClass<*>, Int>()
    private val history = mutableListOf<RetainedInput>()
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
                !sample.timestamp.isBefore(after) && sample.timestamp.isBefore(before) &&
                    (signals.isEmpty() || sample.signal.id in signals)
            }
            .sortedBy { sample -> sample.timestamp }
    }

    override fun getNew(types: List<KClass<*>>): ProcessingInput? {
        val distinctTypes = types.distinct()
        val matchesByType = distinctTypes.associateWith { type ->
            val objectType = type.javaObjectType
            items.mapIndexedNotNull { index, item ->
                if (objectType.isInstance(item)) IndexedValue(index, item) else null
            }
        }
        matchesByType.forEach { (type, matching) ->
            val consumed = consumedCounts[type] ?: 0
            require(consumed <= matching.size) {
                "environment items cannot be removed while consumption is tracked"
            }
        }

        val newlyMatched = BooleanArray(items.size)
        val values = matchesByType.mapValues { (type, matching) ->
            val consumed = consumedCounts[type] ?: 0
            matching.drop(consumed).forEach { match -> newlyMatched[match.index] = true }
            consumedCounts[type] = matching.size
            matching.drop(consumed).map { match -> match.value }
        }

        if (values.values.all(List<Any>::isEmpty)) return null

        val input = ProcessingInput(nextSequence++, values)
        history += RetainedInput(
            input = input,
            items = items.indices.mapNotNull { index ->
                if (newlyMatched[index]) items[index] else null
            },
        )
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
            .mapNotNull { retained ->
                val values = requested.associateWith { type ->
                    val objectType = type.javaObjectType
                    retained.items.filter(objectType::isInstance)
                }
                if (values.values.all(List<Any>::isEmpty)) null
                else retained.input.copy(values = values)
            }
    }

    private data class RetainedInput(
        val input: ProcessingInput,
        val items: List<Any>,
    )
}
