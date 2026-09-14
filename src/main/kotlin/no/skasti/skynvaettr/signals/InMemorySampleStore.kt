package no.skasti.skynvaettr.signals

import java.time.Instant

/** Simple process-local sample store suitable for experiments and default runtime wiring. */
class InMemorySampleStore : MutableSampleStore {
    private val samples = mutableListOf<Sample<*>>()

    override fun append(samples: List<Sample<*>>) {
        this.samples += samples
        this.samples.sortBy(Sample<*>::timestamp)
    }

    override fun get(
        after: Instant,
        before: Instant,
        vararg signals: SignalId,
    ): List<Sample<*>> {
        val selectedSignals = signals.toSet()
        return samples.filter { sample ->
            !sample.timestamp.isBefore(after) &&
                sample.timestamp.isBefore(before) &&
                (selectedSignals.isEmpty() || sample.signal.id in selectedSignals)
        }
    }
}
