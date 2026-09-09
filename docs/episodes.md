# Episodes

Episodes provide a way to select and materialize bounded pieces of signal history for training, evaluation, replay, or other experience-based processing.

The episode model deliberately separates:

- **what experience is selected**, represented by `EpisodeDefinition`;
- **the materialized data for that experience**, represented by `EpisodeData`;
- **the canonical signal history**, which remains the source of truth.

## EpisodeDefinition

`EpisodeDefinition` describes a half-open time interval `[from, to)` and the ordered signals that belong to an episode:

```kotlin
val definition = EpisodeDefinition(
    from = Instant.parse("2026-09-09T10:00:00Z"),
    to = Instant.parse("2026-09-09T10:10:00Z"),
    signals = listOf(indoorTemperature, outdoorTemperature, hvacEffect),
)
```

The signal order is significant because it defines the column order of the corresponding `EpisodeData`.

The definition keeps the actual `Signal<*>` objects rather than only their names. This preserves signal metadata for preprocessing and learned representation layers without repeating that metadata for every sample value.

An episode definition currently contains only:

- start time;
- end time;
- ordered signal definitions.

Model identifiers, labels, rewards, selection scores, or other experiment-specific information should not be added until there is a demonstrated need for them.

## EpisodeData

`EpisodeData` is a compact materialized representation of an episode.

Conceptually:

```text
timestamp       indoor.temp   outdoor.temp   hvac.effect
--------------------------------------------------------
10:00:00        21.5          9.0            0.0
10:01:00        21.4          8.9            0.2
10:02:00        21.4          8.8            0.2
```

In code, the same data is represented as:

```kotlin
EpisodeData(
    definition = definition,
    timestamps = listOf(t0, t1, t2),
    values = listOf(
        listOf(21.5, 9.0, 0.0),
        listOf(21.4, 8.9, 0.2),
        listOf(21.4, 8.8, 0.2),
    ),
)
```

Rows correspond to timestamps.

Columns correspond to `definition.signals`, in exactly the same order.

This avoids storing a full `Sample` object in every matrix cell. Signal identity and metadata are already available through the episode definition, while the timestamp is shared by the whole row.

## Why a 2D representation?

Most model training eventually consumes tensor-like or matrix-like data.

Repeating:

```text
Sample(signal, value, timestamp)
```

for every cell would carry the same signal identity and timestamp information many times. `EpisodeData` instead keeps that information once along the relevant axis.

Conceptually:

```text
signals    = [S0, S1, S2]
timestamps = [T0, T1, T2]

values =
[
  [V00, V01, V02],
  [V10, V11, V12],
  [V20, V21, V22],
]
```

where `Vrc` is the value at row/time `r` for signal/column `c`.

This is a transport and batching representation. It is not itself the model input representation.

## Alignment and asynchronous signals

Real signal sources are not necessarily sampled at the same timestamps.

For example:

```text
indoor.temperature   10:00:00   21.5
outdoor.temperature  10:00:13    9.0
indoor.temperature   10:01:00   21.4
outdoor.temperature  10:01:17    8.9
```

`EpisodeData` does **not** decide how these asynchronous samples become aligned rows.

A separate materialization or preprocessing step must decide whether to use strategies such as:

- exact timestamp matching;
- fixed-interval resampling;
- latest-known value;
- interpolation;
- aggregation over a window;
- missing values.

That policy can materially change what a model learns, so it must remain explicit rather than being hidden inside the episode container.

By the time an `EpisodeData` instance is constructed, the rows are assumed to already be aligned.

## Materializing from SampleStore

The `episodes` package provides a convenience extension:

```kotlin
val data: EpisodeData = sampleStore.get(definition)
```

This keeps `SampleStore` itself generic and unaware of episodes while letting the episode layer adapt stored samples into `EpisodeData`.

The default materializer is intentionally strict. For every timestamp returned by the store, it requires exactly one sample for each signal in the episode definition. It does not:

- interpolate missing signals;
- carry forward previous values;
- aggregate windows;
- deduplicate repeated samples;
- choose between duplicate samples for the same signal and timestamp.

If the raw samples do not already form a rectangular time series, materialization fails rather than silently choosing an alignment policy.

Future alignment or resampling strategies should be introduced explicitly, for example as separate materializers or policies, so that their effect on training data remains visible and testable.

## Validation

The current episode data model enforces several structural invariants:

- the end cannot be before the start;
- an episode must contain at least one signal;
- signals within a definition must be unique;
- there must be exactly one value row per timestamp;
- each row must have exactly one column per signal;
- timestamps must lie inside the episode interval;
- timestamps must be ordered.

These checks are intended to catch corrupt or incorrectly materialized training data early.

## Episodes are not state

An episode is a bounded collection of experience. It is not the same concept as a single world state.

A model may later derive a state representation from:

- one row;
- several preceding rows;
- the entire episode;
- a learned recurrent or transformer context.

Skynvættr therefore does not currently introduce `State` as a core signal abstraction.

## Episodes are not features

`EpisodeData` should remain close to the sensed data.

Model-specific transformations belong later in the pipeline:

```text
SampleStore
    |
    v
EpisodeDefinition
    |
    v
materialize / align
    |
    v
EpisodeData
    |
    v
preprocess / encode / window
    |
    v
model-specific tensors
    |
    v
Model
```

Examples of transformations that should generally happen after episode materialization include:

- normalization;
- delta calculation;
- historical windows;
- learned signal embeddings;
- temporal encodings;
- masking;
- target construction.

Keeping these layers separate lets multiple models train from the same underlying experience using different representations.

## Episode storage

The intended long-term architecture is that an `EpisodeStore` stores episode definitions or episode metadata, while a `SampleStore` provides access to canonical historical samples.

A trainer can then:

1. select an episode;
2. query the relevant samples from the `SampleStore` using the episode time range and signal ids;
3. materialize an `EpisodeData`;
4. apply model-specific preprocessing;
5. train or evaluate the model.

Conceptually:

```text
EpisodeStore ----> EpisodeDefinition
                         |
                         v
SampleStore ------> materialization
                         |
                         v
                    EpisodeData
                         |
                         v
                 preprocessing
                         |
                         v
                       Model
```

This avoids duplicating signal history per model and allows several models or trainers to reinterpret the same experience differently.

See [Signals and samples](signals.md) for the underlying signal model.
