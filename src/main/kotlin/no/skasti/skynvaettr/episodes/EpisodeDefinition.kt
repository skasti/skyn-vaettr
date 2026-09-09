package no.skasti.skynvaettr.episodes

import java.time.Instant
import no.skasti.skynvaettr.signals.Signal

/**
 * Describes which slice of signal history belongs to an episode.
 *
 * The definition keeps the [Signal] objects, rather than only their names, so signal metadata
 * remains available to preprocessing and learned representation layers.
 */
class EpisodeDefinition(
    val from: Instant,
    val to: Instant,
    signals: List<Signal<*>>,
) {
    val signals: List<Signal<*>> = signals.toList()

    init {
        require(!to.isBefore(from)) { "Episode end must not be before its start" }
        require(this.signals.isNotEmpty()) { "Episode must contain at least one signal" }
        require(this.signals.distinct().size == this.signals.size) {
            "Episode signals must be unique"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is EpisodeDefinition &&
            from == other.from &&
            to == other.to &&
            signals == other.signals

    override fun hashCode(): Int {
        var result = from.hashCode()
        result = 31 * result + to.hashCode()
        result = 31 * result + signals.hashCode()
        return result
    }

    override fun toString(): String =
        "EpisodeDefinition(from=$from, to=$to, signals=$signals)"
}
