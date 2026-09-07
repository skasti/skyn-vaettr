package no.skasti.skynvaettr.learning

/**
 * Minimal boundary for a policy that can choose an action and learn from its
 * consequences without any domain-specific understanding of sensor names.
 */
interface AdaptivePolicy {
    fun choose(frame: SensoryFrame): EffectorValue
    fun observe(experience: Experience)
}
