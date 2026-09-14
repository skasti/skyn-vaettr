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

`ProcessingGraph` is the topology boundary. Implementations may be linear pipelines or arbitrary directed graphs with fan-out, feedback loops, stateful modules, independent update schedules, and learned components. Core does not yet prescribe ports, scheduling, or module lifecycle.

Experiments therefore exercise Skynvættr through the same top-level API rather than invoking isolated model classes directly:

```kotlin
val vaettr = Vaettr(experimentGraph)

world.simulate(...) { samples ->
    vaettr.sense(samples)
}
```

A plain `Vaettr()` uses `SensingProcessingGraph` as its initial default.

## Default sensing graph

`SensingProcessingGraph` carries forward the generic sensory-input structure that performed best in the temporal relation-discovery experiments without promoting experiment-specific predictors or targets into core.

For every observed signal it keeps timestamped history and selects the same generic log-spaced ages used in those experiments:

```text
5120, 2560, 1280, 640, 320, 160, 80, 60, 40, 30, 20, 15, 10, 5, 0 seconds
```

Each selected observation becomes one position containing:

```text
[ signal identity embedding | scalar value | normalized relative time ]
```

The default currently supports numeric and boolean sample values because those are the value domains exercised by the promoted playpen experiments. Other value encodings should be introduced through evidence rather than guessed in core.

The graph deliberately stops at this generic input `Representation`. The strongest playpen attention result used normalized values plus learned input/key/value projections and a learned latent query trained from a prediction objective. Applying scaled dot-product attention directly to raw sensory vectors would therefore be a different, unvalidated model. Learned contextualization belongs in later graph components once training/model ownership is established.

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

`ScaledDotProductAttention` is the current baseline implementation and computes:

```text
softmax(Q K^T / sqrt(d_k)) V
```

It deliberately does not own Q/K/V projection matrices, optimizer state, prediction heads, normalization, or training policy. Those remain concerns of the model/module using attention.

## Current boundary

This revision establishes:

- `Vaettr` as the external sensory entry point;
- `ProcessingGraph` as the replaceable internal topology boundary;
- `SensingProcessingGraph` as the initial default sensory front-end;
- `Representation`/`Embedding` as generic latent numeric data;
- replaceable embedding and attention contracts with current baseline implementations.

It does not yet define graph ports, graph scheduling, a module interface, a Transformer, working-memory semantics, effector routing, an optimizer, or a training lifecycle. Those should be introduced from experiments that exercise the `Vaettr` entry point rather than designed in isolation.
