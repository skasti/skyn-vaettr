# Sensory representation primitives

Skynvættr keeps the stages that turn observations into model context separate so experiments can replace one stage without redefining the others.

```text
SignalId
   |
   v
SignalTokenizer
   |
   v
lexical Tokens
   |
   v
TokenEncoder
   |
   v
per-token numeric representations
   |
   v
SignalIdentityEmbedder
   |
   v
signal identity Embedding
   |
   +--- sample value / relative time / other observable model input
   |
   v
model-specific learned projection
   |
   +----> Q
   +----> K
   +----> V
            |
            v
ScaledDotProductAttention
            |
            v
context Embeddings
```

## Embedding

`Embedding` is a small immutable value type for a finite fixed-width numeric representation. The type deliberately says nothing about how the vector was produced. An embedding may therefore be deterministic, learned, lexical, contextual, or the output of a Q/K/V projection.

`Embedder<T>` is the corresponding boundary for components that produce embeddings. Training is intentionally not part of this interface: a learned implementation may be owned and updated by a `Model`/`Trainer`, while deterministic implementations need no training at all.

`SignalIdentityEmbedder` is the initial source-agnostic baseline. It tokenizes a `SignalId`, encodes each lexical token using the existing `TokenEncoder`, and mean-pools those token representations into one signal-identity embedding. With the default `DeterministicByteTokenEncoder`, this is **not** a learned semantic embedding; it is simply a stable compositional representation on which learned model layers can build.

## Attention

`ScaledDotProductAttention` implements the standard operation

```text
softmax(Q K^T / sqrt(d_k)) V
```

for embeddings that have already been projected to Q, K, and V.

The attention primitive deliberately does **not** own projection matrices, optimizer state, prediction heads, sample normalization, or training policy. Those are model concerns and remain free to evolve independently.

This separation is important for Skynvættr experiments: a model may combine signal identity with observable sample value and temporal information before learning Q/K/V projections, without teaching core which domains, signals, history windows, or prediction targets are important.

## Current boundary

This layer provides enough shared structure for playpen models to use the same tokenization, embedding, and attention vocabulary as Skynvættr itself. It does not yet define a Transformer, multi-head attention, positional encoding, value normalization, an optimizer, or a training lifecycle. Those should only move into core when experiments establish a stable abstraction for them.
