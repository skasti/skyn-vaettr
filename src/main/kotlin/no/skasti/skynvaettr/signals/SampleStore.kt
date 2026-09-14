package no.skasti.skynvaettr.signals

import java.time.Instant

/**
 * Provides access to canonical historical [Sample] records.
 *
 * Implementations must return samples in chronological order for the half-open interval
 * [after, before). Supplying no signal ids means all signals.
 *
 * SampleStore deliberately imposes no uniqueness constraint on timestamps. Multiple samples
 * may share the same timestamp, including multiple samples for the same [SignalId]. No
 * deduplication, state inference, resampling, or episode semantics are performed here.
 */
interface SampleStore {
    fun get(
        after: Instant,
        before: Instant,
        vararg signals: SignalId,
    ): List<Sample<*>>

    fun get(
        after: Instant,
        vararg signals: SignalId,
    ): List<Sample<*>> =
        get(after, Instant.now(), *signals)

    /** Signal identities observed by this store so far. Implementations may override efficiently. */
    fun signalIds(): Set<SignalId> =
        get(Instant.MIN, Instant.MAX).mapTo(linkedSetOf()) { it.signal.id }

    /** Latest sample for [signal] whose timestamp is not after [at], or null when none exists. */
    fun latestAtOrBefore(
        signal: SignalId,
        at: Instant,
    ): Sample<*>? {
        val before = if (at == Instant.MAX) Instant.MAX else at.plusNanos(1)
        return get(Instant.MIN, before, signal).lastOrNull()
    }
}
