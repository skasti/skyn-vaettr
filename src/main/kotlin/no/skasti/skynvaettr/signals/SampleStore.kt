package no.skasti.skynvaettr.signals

import java.time.Instant

/**
 * Provides access to canonical historical [Sample] records.
 *
 * Implementations must return samples in chronological order for the half-open interval
 * [after, before). An empty signal-id collection means all signals.
 *
 * SampleStore deliberately imposes no uniqueness constraint on timestamps. Multiple samples
 * may share the same timestamp, including multiple samples for the same [SignalId]. No
 * deduplication, state inference, resampling, or episode semantics are performed here.
 */
interface SampleStore {
    fun get(
        after: Instant,
        before: Instant,
        signals: Collection<SignalId> = emptyList(),
    ): List<Sample<*>>

    fun get(
        after: Instant,
        signals: Collection<SignalId> = emptyList(),
    ): List<Sample<*>> =
        get(after, Instant.now(), signals)
}
