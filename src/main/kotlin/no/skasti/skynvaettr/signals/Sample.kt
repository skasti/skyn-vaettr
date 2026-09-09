package no.skasti.skynvaettr.signals

import java.time.Instant

/**
 * One value received for a [Signal] at a specific point in time.
 *
 * Sample deliberately does not imply that the value remains valid until another sample
 * arrives. Stateful and event-like semantics can be layered on later if needed.
 */
data class Sample<T>(
    val signal: Signal<T>,
    val value: T,
    val timestamp: Instant,
)
