package no.skasti.skynvaettr.examples

import java.time.Duration
import java.time.Instant
import kotlin.random.Random
import no.skasti.skynvaettr.Vaettr
import no.skasti.skynvaettr.environment.InMemoryEnvironment
import no.skasti.skynvaettr.signals.Sample
import no.skasti.skynvaettr.signals.Signal

/**
 * Example world with two related numeric states.
 *
 * The dimmer changes a few times during each daytime period and remains at each level for a long
 * time. Every day gets a different deterministic schedule. Between 23:00 and 06:00 the dimmer is
 * always off. The light follows the dimmer after [lightDelay], so dimmer changes lead the observed
 * light state without exposing that relationship to Skynvættr.
 */
class KitchenLightScenario(
    val dimmer: Signal<Double> = Signal("state.kitchen.dimmer"),
    val light: Signal<Double> = Signal("state.kitchen.light"),
    private val lightDelay: Duration = Duration.ofMinutes(10),
    private val seed: Int = 73,
) : InMemoryEnvironment() {
    init {
        require(!lightDelay.isNegative)
    }

    fun simulate(
        vaettr: Vaettr,
        duration: Duration,
        step: Duration,
        start: Instant = Instant.EPOCH,
    ) {
        require(vaettr.environment === this) { "Vaettr must use this scenario as its environment" }
        require(!duration.isNegative && !duration.isZero)
        require(!step.isNegative && !step.isZero)

        val totalSteps = duration.toNanos() / step.toNanos()
        val schedules = mutableMapOf<Long, DaySchedule>()

        for (index in 0..totalSteps) {
            val timestamp = start.plus(step.multipliedBy(index))
            val elapsed = Duration.between(start, timestamp)
            val day = elapsed.toDays()
            val secondOfDay = elapsed.minusDays(day).seconds

            val dimmerValue = valueAt(day, secondOfDay, schedules)
            val delayedElapsed = elapsed.minus(lightDelay)
            val lightValue = if (delayedElapsed.isNegative) {
                0.0
            } else {
                val delayedDay = delayedElapsed.toDays()
                val delayedSecondOfDay = delayedElapsed.minusDays(delayedDay).seconds
                valueAt(delayedDay, delayedSecondOfDay, schedules)
            }

            append(
                listOf(
                    Sample(dimmer, dimmerValue, timestamp),
                    Sample(light, lightValue, timestamp),
                ),
            )
            vaettr.update()
        }
    }

    private fun valueAt(
        day: Long,
        secondOfDay: Long,
        schedules: MutableMap<Long, DaySchedule>,
    ): Double {
        val hour = secondOfDay.toDouble() / Duration.ofHours(1).seconds.toDouble()
        if (hour < 6.0 || hour >= 23.0) return 0.0

        val schedule = schedules.getOrPut(day) { createSchedule(day) }
        return schedule.changes.lastOrNull { it.hour <= hour }?.value ?: schedule.initialValue
    }

    private fun createSchedule(day: Long): DaySchedule {
        val random = Random(seed + day.toInt() * 997)
        val levels = listOf(0.15, 0.30, 0.45, 0.60, 0.75, 0.90)
        val initial = levels[random.nextInt(levels.size)]
        val changeCount = random.nextInt(2, 5)
        val availableHours = (8..21).shuffled(random).take(changeCount).sorted()
        var previous = initial
        val changes = availableHours.map { hour ->
            val candidates = levels.filter { it != previous }
            val next = candidates[random.nextInt(candidates.size)]
            previous = next
            LevelChange(hour = hour + random.nextDouble(-0.35, 0.35), value = next)
        }
        return DaySchedule(initial, changes)
    }

    private data class DaySchedule(
        val initialValue: Double,
        val changes: List<LevelChange>,
    )

    private data class LevelChange(
        val hour: Double,
        val value: Double,
    )
}
