# Default topology

`DefaultTopology` is the initial concrete implementation of the [topology contract](topology.md).
It provides a small synchronous network for experiments while keeping `Topology` independent of any
particular processing model.

## Construction and update

`Vaettr` owns an `Environment` and a `Topology`. When no topology is supplied, it creates a
`DefaultTopology` for that environment. `Vaettr.update()` delegates directly to `Topology.update()`.

`DefaultTopology` receives a list of `Node` instances and copies it into its read-only `nodes` list.
Its `entryPoints` list is derived from those nodes by selecting the `EntryPoint<*>` instances. Other
nodes remain part of the topology even though they are not polled directly by the environment.

On each update, `DefaultTopology`:

1. collects the distinct input types declared by its entrypoints;
2. calls `Environment.getNew(type)` once for each distinct type;
3. passes each non-empty batch to every entrypoint declaring that type.

This means two entrypoints with the same input type receive the same batch. Empty batches do not call
`EntryPoint.process`. Processing and port delivery are synchronous in this implementation.

## Current example network

The example reports build the following topology using identity Q/K/V projections:

```mermaid
flowchart LR
    S[SampleEntryPoint] --> Q[QueryNode]
    S --> K[KeyNode]
    S --> V[ValueNode]
    Q --> AQ[q]
    K --> AK[k]
    V --> AV[v]
    AQ --> A[AttentionNode]
    AK --> A
    AV --> A
```

`SampleEntryPoint` stores incoming samples in its `MutableSampleStore`, selects the configured
history ages for each signal, and emits one `Representation` containing signal identity, scalar
value, and normalized relative time for each selected observation.

`QueryNode`, `KeyNode`, and `ValueNode` each have an input and output port. Their default
`IdentityRepresentationProjection` keeps the sample representation unchanged. A separate
`LinearRepresentationProjection` can be supplied to each node with its own weights and bias. The
projection is applied independently to every position in the input representation.

`AttentionNode` waits until its `q`, `k`, and `v` ports each contain a representation. It invokes its
configured `Attention` implementation, clears those three inputs after successful computation and
before output delivery, and emits `AttentionResult.output` through its `attention` port. If attention
computation fails, the inputs remain available. A failure while delivering the output happens after
the inputs have been cleared.

## Port and delivery behaviour

The generic `Port<T>` contract exposes its name, receive event, outgoing `synapses`, and send/receive
operations. The current `SingleSlotPort<T>` implementation retains zero or one value, rejects a new
value while occupied, and exposes `pending` and `clear()` to nodes that use that buffering policy.

Connections are created by calling `source.connectTo(target)` before the topology is constructed.
One source port may fan out to several target ports. `DefaultTopology` does not yet validate that
every connected target belongs to its `nodes` list, nor does it own a separate connection registry.

## Report rendering

The reporting source set uses `TopologyRenderer` to inspect `Topology.nodes`, each node's `ports`, and
each port's `synapses`. It writes `topology.dot` and invokes Graphviz to produce `topology.png`.
Ports are not drawn as separate nodes; each directed arrow connects the nodes that own the source and
target ports. Multiple connections between the same pair of nodes are rendered as one arrow, while
disconnected nodes remain visible.

Install Graphviz and make `dot` available on `PATH`, or set `GRAPHVIZ_DOT` to the executable path.
The example reports can then be rendered with `./gradlew renderExampleReports` or
`gradlew.bat renderExampleReports` on Windows. CI installs Graphviz and publishes the topology image
alongside the example charts.

## Deliberate limits

`DefaultTopology` currently provides synchronous delivery, single-slot buffering, and direct
entrypoint polling. It does not define asynchronous scheduling, retries, queue policies, multi-input
correlation, topology-level validation, or training/update rules for projection parameters. Those
remain experiments and design questions for future topology implementations.
