package no.skasti.skynvaettr.learning

/**
 * Global feedback about an experienced consequence.
 *
 * It deliberately carries no information about which sensor or action was
 * "correct". Positive values mean that the consequence should be reinforced,
 * negative values mean that it should be discouraged, and zero is neutral.
 */
@JvmInline
value class Consequence(val value: Double) {
    init {
        require(value.isFinite() && value in -1.0..1.0) {
            "Consequence must be finite and in [-1.0, 1.0]"
        }
    }
}
