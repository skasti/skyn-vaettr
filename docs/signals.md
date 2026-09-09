# Signals and samples

Skynvættr represents sensed data using a few deliberately small core abstractions:

- `SignalId` is the stable identity of a signal.
- `Signal<T>` describes **what can be sampled**.
- `Sample<T>` records **one value observed for a signal at a particular instant**.
- `SampleStore` provides generic access to historical samples.

Keeping these concepts separate gives the runtime stable signal identity and rich semantic metadata without conflating a signal definition with any particular observed value.

## Signal

A signal is the definition of one uniquely named value stream.

For example:

```kotlin
val temperature = Signal<Double>(
    id = SignalId("sensor.kontor_presence_temperature"),
    metadata = mapOf(
        "state_class" to "measurement",
        "unit_of_measurement" to "°C",
        "device_class" to "temperature",
        "friendly_name" to "Kontor Presence Temperature",
    ),
)
```

`SignalId` is a small value type rather than a raw string so APIs can distinguish signal identity from arbitrary textual metadata. Two `Signal` instances with the same id are considered equal even if their metadata differs.

Signal ids must be globally stable within the runtime. Metadata may evolve, but changing the id means referring to a different signal.

For convenience, `Signal` also accepts a string constructor, but APIs that exchange identity without the full signal definition should use `SignalId`.

## Metadata

Signal metadata is intentionally open-ended.

The core does not currently prescribe concepts such as `Channel`, units, device classes, source types, ranges, or semantic categories as first-class abstractions. Integrations can attach the metadata that is useful for their domain.

This is deliberate for two reasons:

1. Skynvættr should remain usable across very different environments.
2. Future learned representations may use signal identity and metadata to discover relationships between signals without requiring those relationships to be hard-coded into the core model.

For example, several independently named signals may all contain metadata indicating that they represent temperature. A later encoder can use that information while still preserving their individual identities.

Metadata should therefore be treated as semantic information about the signal definition, not as per-sample payload.

## Sample

A sample is one observed value for a signal:

```kotlin
val sample = Sample(
    signal = temperature,
    value = 25.9,
    timestamp = Instant.parse("2026-09-09T11:00:00Z"),
)
```

A sample answers three questions:

- which signal was sampled?
- what value was observed?
- when was it observed?

A sample does **not** say that the value remains current until another sample arrives.

This temporal neutrality is important. Some signals may describe continuously changing measurements, others may represent sparse events, and still others may eventually need stateful or impulse-like semantics. Those distinctions should be introduced only when experiments show that the runtime needs them.

## Signal history

Historical samples are the canonical record of sensed experience.

Models may derive many different representations from the same history:

```text
Signal definitions + Samples
          |
          +--> normalization / preprocessing
          |
          +--> learned signal encoder
          |
          +--> temporal encoder
          |
          +--> model-specific features or latent representations
```

The raw signal history should remain independent of any particular model representation so that historical experience can be reinterpreted when encoders, architectures, normalization strategies, or training objectives change.

## SampleStore

`SampleStore` is the generic source of historical samples. It deliberately knows nothing about episodes, training, resampling, or model state.

Its core query is:

```kotlin
fun get(
    after: Instant,
    before: Instant,
    signals: Collection<SignalId> = emptyList(),
): List<Sample<*>>
```

The interval is half-open: `[after, before)`. Results are returned in chronological order. If the signal-id collection is empty, the query applies to all signals. A collection is used rather than `vararg SignalId` because Kotlin prohibits value classes such as `SignalId` as vararg element types.

A convenience overload uses the current instant as `before`:

```kotlin
sampleStore.get(after, listOf(indoorTemperature.id, outdoorTemperature.id))
```

The store places no uniqueness constraint on timestamps. All of the following are valid:

- multiple different signals sampled at the same timestamp;
- multiple samples for the same signal at the same timestamp;
- irregular sampling intervals;
- sparse or bursty signals.

`SampleStore` must not silently deduplicate these records or infer state from them. Likewise, alignment, interpolation, aggregation, and episode construction belong to later layers.

## Learned representations

A future encoder may combine information such as:

- signal identity;
- signal metadata;
- the observed value;
- temporal information;
- surrounding samples;
- relationships to other signals.

Conceptually:

```text
Signal metadata + value + temporal context
                  |
                  v
               encoder
                  |
                  v
       learned representation
```

For continuous values, arbitrary tokenization or bucketing is not assumed. A model may encode numerical values directly while separately learning representations of signal identity and metadata.

This separation is also intended to make it possible for models to generalize to newly introduced signals. A new signal should not necessarily require rebuilding a model merely because it has a new name; its metadata and observed behaviour may provide enough structure for a shared encoder to interpret it.

Exactly how well this works is an experimental question and should not be baked into the signal API prematurely.

## Relationship to episodes

Signals and samples describe the canonical sensed data.

Episodes describe **which parts of that history should be treated together as an experience**, for example for training or evaluation.

An episode therefore references signal definitions and a time interval rather than replacing or duplicating the underlying sample history.

See [Episodes](episodes.md) for the episode data model.
