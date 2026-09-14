package no.skasti.skynvaettr.training

import kotlin.random.Random

/** Selects one previously resolved experience for an additional replay update. */
fun interface ReplaySelector<E> {
    fun select(candidates: List<E>): E?
}

/**
 * Samples experiences proportionally to a caller-provided priority while preserving a small floor
 * so low-priority experiences are not made permanently unreachable.
 */
class WeightedPriorityReplaySelector<E>(
    private val priority: (E) -> Double,
    seed: Int = 1,
    private val floor: Double = 0.05,
) : ReplaySelector<E> {
    private val random = Random(seed)

    init {
        require(floor.isFinite() && floor > 0.0) { "Replay floor must be finite and positive" }
    }

    override fun select(candidates: List<E>): E? {
        if (candidates.isEmpty()) return null

        val weights = candidates.map { candidate ->
            val value = priority(candidate)
            require(value.isFinite() && value >= 0.0) {
                "Replay priority must be finite and non-negative"
            }
            maxOf(value, floor)
        }
        val total = weights.sum()
        var cursor = random.nextDouble() * total

        candidates.forEachIndexed { index, candidate ->
            cursor -= weights[index]
            if (cursor <= 0.0) return candidate
        }
        return candidates.last()
    }
}
