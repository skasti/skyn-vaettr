package no.skasti.skynvaettr.learning

/**
 * An immutable snapshot of ordinary numeric sensor values available to the entity.
 *
 * Values are defensively copied so later mutations of a caller-owned map cannot
 * alter historical experiences.
 */
class SensoryFrame(values: Map<String, Double>) {
    val values: Map<String, Double> = values.toMap()

    init {
        require(this.values.keys.none(String::isBlank)) { "Sensor names must not be blank" }
        require(this.values.values.all(Double::isFinite)) { "Sensor values must be finite" }
    }

    operator fun get(name: String): Double? = values[name]

    override fun equals(other: Any?): Boolean =
        other is SensoryFrame && values == other.values

    override fun hashCode(): Int = values.hashCode()

    override fun toString(): String = "SensoryFrame(values=$values)"
}
