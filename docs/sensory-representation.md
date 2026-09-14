# Representations and processing primitives

Skynvættr treats a running `Vaettr` as the external entry point and keeps its internal processing topology replaceable.

```text
World / adapters
      |
      | Samples
      v
    Vaettr
      |
      v
ProcessingGraph
   /   |    \
  v    v     v
perception  memory  higher-level models
    \        /
     \      /
   Representation
```

`ProcessingGraph` is intentionally only a boundary in this revision. An implementation may later be a linear pipeline or an arbitrary directed graph with fan-out, feedback loops, stateful modules, independent update schedules, and learned components. Core does not yet prescribe ports, scheduling, or module lifecycle.

This lets experiments exercise Skynvættr through the same top-level API rather than invoking isolated model classes directly:

```kotlin
val vaettr = Vaettr(experimentGraph)

world.simulate(...) { samples ->
    vaettr.sense(samples)
}
```

## Representation

`Representation` is the generic internal numeric form exchanged between model components. It is a two-dimensional sequence of positions × dimensions. Core deliberately assigns no semantic meaning to either axis: positions may represent observations, tokens, time steps, memory slots, or another learned structure.

`Embedding` is one fixed-width vector, and therefore one row/position of a `Representation`.

This boundary is intentionally close to the tensor-like hidden-state representation used inside neural models. Human-readable identities and diagnostics do not need to be decoded and re-encoded between every internal component.

## Signal identity embedding

`Embedder<T>` defines a replaceable boundary for components that produce embeddings. Training is intentionally not part of the interface: implementations may be deterministic or learned.

`SignalIdentityEmbedder` is the current source-agnostic baseline. It tokenizes a `SignalId`, encodes each lexical token using the existing `TokenEncoder`, and mean-pools those token representations into one signal-identity embedding. With the default `DeterministicByteTokenEncoder`, this is not a learned semantic embedding; it is a stable compositional representation on which learned components can build.

Tokenization and signal embedding are not global Skynvættr pipeline stages. They are primitives a perception implementation may use internally.

## Attention

`Attention` defines a replaceable operation over query, key, and value `Representation` values.

`ScaledDotProductAttention` is the current default implementation and computes:

```text
softmax(Q K^T / sqrt(d_k)) V
```

It deliberately does not own Q/K/V projection matrices, optimizer state, prediction heads, normalization, or training policy. Those remain concerns of the model/module using attention.

## Current boundary

This revision intentionally establishes only:

- `Vaettr` as the external sensory entry point;
- `ProcessingGraph` as the replaceable internal topology boundary;
- `Representation`/`Embedding` as generic latent numeric data;
- replaceable embedding and attention contracts with current baseline implementations.

It does not yet define graph ports, graph scheduling, a module interface, a Transformer, working-memory semantics, effector routing, an optimizer, or a training lifecycle. Those should be introduced from experiments that exercise the `Vaettr` entry point rather than designed in isolation.
