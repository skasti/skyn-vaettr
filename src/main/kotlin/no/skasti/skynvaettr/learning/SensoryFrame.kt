package no.skasti.skynvaettr.learning

/** A snapshot of ordinary numeric sensor values available to the entity. */
data class SensoryFrame(val values: Map<String, Double>) {
    init {
        require(values.keys.none(String::isBlank)) { "Sensor names must not be blank" }
        require(values.values.all(Double::isFinite)) { "Sensor values must be finite" }
    }

    operator fun get(name: String): Double? = values[name]
}
