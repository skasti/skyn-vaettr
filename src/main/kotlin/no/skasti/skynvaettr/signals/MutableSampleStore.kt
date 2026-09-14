package no.skasti.skynvaettr.signals

/**
 * Writable canonical history of observations received by a running Skynvættr.
 *
 * Runtime ingestion writes every observed [Sample] here before downstream processing is notified.
 * Trainers and processing graphs consume the same history through [SampleStore].
 */
interface MutableSampleStore : SampleStore {
    fun append(samples: List<Sample<*>>)

    fun append(sample: Sample<*>) = append(listOf(sample))
}
