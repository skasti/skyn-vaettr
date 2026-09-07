package no.skasti.skynvaettr.learning

/** Normalized scalar command for a continuous effector. */
@JvmInline
value class EffectorValue(val value: Double) {
    init {
        require(value.isFinite() && value in -1.0..1.0) {
            "Effector value must be finite and in [-1.0, 1.0]"
        }
    }
}
