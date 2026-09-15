# Predictions and Expectations

This document defines the current prediction/expectation boundary in Skynvættr core and the active learning experiment.

## Current experiment: learn predictions before expectations

The thermal and kitchen examples currently stop at `Prediction`. They intentionally do **not** create `Expectation` instances while we validate whether a learned attention model can discover useful temporal relationships and predict the next meaningful transition.

Expectation types, policies, results and replay infrastructure remain in core for later use; they are not removed by this experiment.

The active example path is deliberately explicit in code:

```mermaid
flowchart LR
    S[Samples]
    --> H[History selection]
    --> T[SignalId tokenization]
    --> E[Token + sensory encoding]
    --> R[Representation]
    --> A[Self-attention Q/K/V]
    --> C[Contextualized positions]
    --> PH[Transition head]
    --> O[Latent transition representation]
    --> D[TransitionPredictionDecoder]
    --> P[Prediction]

    P -. later .-> EP[ExpectationPolicy]
    EP -. later .-> X[Expectation]
```

`SensoryRepresentationEncoder` owns history selection and sensory encoding. `SignalIdentityEmbedder` performs the signal-id tokenization/identity encoding inside that stage. `TransitionPredictionProcessingGraph` owns transition detection, the learned attention model and the decoder, and therefore owns the complete inference path. `TransitionPredictionTrainer` only supervises already-completed graph executions.

## Representation and signal identity

Each sensory position is encoded from signal identity, scalar value and actual relative time. `Representation` itself remains a generic sequence of embeddings and does not carry symbolic `SignalId` fields.

The signal identity embedding is nevertheless part of the numeric representation seen by the model. This allows learned model output to contain a latent signal identity that can later be decoded back to a known signal.

Diagnostic metadata (`SensoryPosition`) is kept alongside the representation only so reports can label attention weights with the original signal and timestamp. Prediction decoding does not use that metadata to choose a target signal.

```mermaid
flowchart LR
    ID[SignalId]
    --> TOK[SignalTokenizer]
    --> TE[TokenEncoder]
    --> SI[Signal identity embedding]

    V[Scalar value] --> E[Encoded sensory position]
    RT[Actual relative time] --> E
    SI --> E
    E --> R[Representation]

    ID -. diagnostics only .-> H[Attention report labels]
```

## Horizon-free next-transition prediction

`Prediction<T>` deliberately has no mandatory target timestamp or configured `+N` horizon.

The active self-supervised objective is event-conditioned only in **when inference runs**: after a meaningful transition, the graph asks the model to predict the next meaningful numeric transition. The transition detector does not create a special query token. The model instead self-attends over the complete sensory history representation. When a later meaningful transition is observed, its signal identity and value become the training target for the earlier graph execution.

```mermaid
sequenceDiagram
    participant G as Processing graph
    participant M as Self-attention model
    participant P as Prediction
    participant T as Trainer

    G->>G: detect meaningful transition
    G->>M: complete sensory Representation
    M->>M: every position Q attends to all K/V positions
    M->>G: latent transition output
    G->>P: decode target signal + value
    G->>G: later meaningful transition
    G->>T: completed new transition execution
    T->>M: train previous exact input toward observed transition
```

The trainer does not own the model, decoder or transition detector. It trains the exact graph-owned model/input pair that produced the pending prediction.

This differs from the earlier next-sense-cycle objective, which accidentally behaved like a hidden polling-interval horizon and overrepresented plateaus.

## Meaningful transitions

`NumericTransitionEventDetector` is the current runtime baseline. It measures accumulated movement from the last accepted transition anchor rather than requiring one large sample-to-sample jump.

A discrete dimmer change may therefore transition immediately, while many small thermal movements can accumulate into a transition.

```mermaid
flowchart LR
    A[Anchor] --> B[small movement]
    B --> C[small movement]
    C --> D{meaningful accumulated change?}
    D -->|no| B
    D -->|yes| E[Trigger prediction / new anchor]
```

The current range-fraction threshold is an experimental baseline, not intended as the final salience mechanism.

## Learned self-attention baseline

`LearnedAttentionTransitionModel` is intentionally smaller than a Transformer. Every sensory position is projected independently to Q, K and V. Scaled dot-product self-attention then contextualizes every position against every other position. A simple mean-pooling baseline combines those contextualized positions before a learned transition head emits latent target-signal identity and numeric value.

```mermaid
flowchart LR
    R[History Representation] --> Q[Learned Q projections]
    R --> K[Learned K projections]
    R --> V[Learned V projections]
    Q --> A[Scaled dot-product self-attention]
    K --> A
    V --> A
    A --> C[Contextualized positions]
    C --> P[Mean pool]
    P --> S[Latent target signal identity]
    P --> N[Numeric value]
```

All contextualized positions contribute to the prediction loss. Gradients therefore train every attention row, making relationships such as `state.kitchen.light` querying `state.kitchen.dimmer` inspectable and learnable rather than leaving non-source query rows unused.

This is an inspectable Q/K/V baseline rather than the final model architecture. A later model may use multiple heads, learned pooling/readout tokens, residual blocks or longer-lived memory.

## Decoding target signal identity

`TransitionPredictionDecoder` observes which numeric signals currently exist, but does not decide the target signal from input metadata.

The model output contains a latent signal-identity vector. The decoder matches that vector against embeddings of known signals and returns the nearest signal together with decoded value and confidence.

That means the learner must actually discover that a context such as a dimmer transition should map to `state.kitchen.light`; the decoder cannot obtain that answer from report metadata.

## Inspectability

The examples report days 1, 3, 5 and 10 so learning can be inspected over time.

Two diagnostics are produced per snapshot:

1. **Self-attention relationship heatmap** — rows are query signals and columns are attended-to signals. Each query-position attention row is normalized by the model and rows are then averaged by query signal, so the report can directly show e.g. `state.kitchen.light → state.kitchen.dimmer`.
2. **Target-signal matrix** — normalized actual-vs-predicted next-transition signal identity.

Attention is not treated as proof of causality. It tells us what the model weighted while making a prediction; prediction accuracy and later ablation experiments provide stronger evidence that a relationship is actually being used.

## Expectations remain a separate layer

`Expectation<T>`, `ExpectationResult`, `ExpectationPolicy<T>`, `NumericExpectationPolicy`, `ExpectationTrainer` and replay infrastructure remain available in core.

They represent a different concern: deciding when a model-produced prediction is important and reliable enough for the running Skynvættr to retain as a persistent belief.

The intended later layering remains:

```mermaid
flowchart LR
    M[World model] --> P[Predictions]
    P --> G[ExpectationPolicy]
    G --> E[Expectation]
    E --> R[Resolution / surprise]
    R --> L[Optional replay / significance learning]
    L --> M
```

We are intentionally postponing that layer until the prediction/QKV path is sufficiently understood.
