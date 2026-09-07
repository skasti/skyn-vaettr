package no.skasti.skynvaettr.learning

/** One closed-loop experience from sensed state through action to outcome. */
data class Experience(
    val before: SensoryFrame,
    val action: EffectorValue,
    val after: SensoryFrame,
    val consequence: Consequence,
)
