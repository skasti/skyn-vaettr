# Learnable processing chain (initial core integration)

This is an **opt-in topology composition**, not a replacement for `DefaultTopology`.
It introduces trainable linear projection/decoder nodes with recorded forward passes and
revision-checked parameter replacement. It deliberately does not import the playpen's
outdoor-light experiment, replay selectors, confidence objectives, or signal-specific labels.

```mermaid
flowchart LR
    R["Representation"] --> F["Fanout"]
    F --> Q["Trainable Q"]
    F --> K["Trainable K"]
    F --> V["Trainable V"]
    Q --> A["Attention"]
    K --> A
    V --> A
    A --> D["Trainable decoder"]
    D --> O["Representation"]
    T["External trainer"] -. "snapshots and updates" .-> Q
    T -. "snapshots and updates" .-> K
    T -. "snapshots and updates" .-> V
    T -. "snapshots and updates" .-> D
```

`LearnableProcessingChain` exposes ordinary `Node` instances, an input and output
`Port<Representation>`, so its nodes can be passed to `DefaultTopology(nodes = ...)` and
its input connected to an existing `SampleEntryPoint.output`. The topology does not
automatically attach a trainer or alter how Samples are sensed.

Each `TrainableLinearNode` accepts a fixed-shape `LinearParameters` (weights and bias),
produces a position-wise linear output and an inspectable `LinearForwardPass`, and
exposes `snapshotRequests`, `parameterSnapshots`, and `parameterUpdates`. The recorded
pass retains the exact input, output, parameter snapshot and revision, and can compute a
vector-Jacobian product via `backward(outputGradient)`. The result contains input,
weight and bias gradients. Arrays are copied at the public boundary.

An external trainer may compute its own objective/learning rate, use
`LinearParameters.descend(gradients, learningRate)`, then submit
`LinearParameterUpdate(expectedRevision, newParameters)`. A stale revision or a shape
change is rejected, and neither the node nor `DefaultTopology` decides how/when to
train. Frozen evaluation can simply omit parameter updates.

### Current limit and next validation

The current `AttentionNode` exposes forward attention only. **This initial PR does not
provide gradients through attention or demonstrate end-to-end training of Q/K/V from
prediction error.** The Q/K/V linear nodes are individually trainable from supplied
gradients, but a future change must provide recorded attention VJPs and join the actual
forward passes before a generic trainer can propagate decoder error to all stages.
Do not replace that missing derivative with heuristic updates or misleading claims.

Before promoting learned binary semantics, confidence gating, retention quotas or replay
policies, reproduce improvements on multiple seeds and signal identities, including
unseen identifiers, matched optimization budgets, per-horizon routing coverage and
frozen no-update evaluation. See the [playpen PR21 findings](https://github.com/skasti/vaettr-playpen/blob/main/docs/pr21-findings.md).

This opt-in design follows [Architecture](architecture.md), [Default topology](default-topology.md)
and [Sensory representation](sensory-representation.md). It does not change `Signal`,
`SignalId`, `Sample` or `Prediction` contracts.
