# Predictions and Expectations

This document defines the minimal prediction/expectation boundary currently promoted into Skynvættr core.

## Prediction

`Prediction<T>` is a model-produced value for one `Signal<T>` together with the model's current confidence in that prediction.

Confidence is normalized to `0.0..1.0`. It describes the model's own support for the prediction; it does **not** mean that the runtime has committed to believing it, and it is not necessarily a calibrated probability of correctness.

Predictions deliberately have no creation time, mandatory target timestamp, or fixed forecast horizon. Timing, ordering and other temporal semantics belong to the model/runtime context around a prediction rather than to the value type itself.

## Learning what to predict

Autonomous expectation discovery requires the model to learn from observations which future signal states, events, or changes are worth predicting, what values or changes to predict, and which relationships help make those predictions. One learned component may propose predictions, or several learned nodes may share that responsibility.

Deterministic adapters may prepare observations by encoding, normalizing, or tokenizing measurements and preserving signal history. In a discovery experiment, an adapter must not select the target signal, expected value or direction, or fulfilment condition on the model's behalf. The expectation gate consumes model-produced predictions and decides whether to commit to them. Its decisions concern the lifecycle of the belief.

### Training and independent evaluation

An experiment may specify a relationship it hopes the model will discover. Use that hypothesis to evaluate model-produced predictions independently. Keep the relationship out of the model inputs, candidate generation, and training target selection. General self-supervised targets derived from observations are allowed. For example, later observations across available signals can provide training targets using a common rule that applies regardless of the relationship under evaluation.

- **Autonomous discovery.** Give the model observations that include illuminance and outdoor-light state among other signals. Let it learn which signal and change to predict. Evaluate on held-out observations whether its predictions capture how the light responds to illuminance. Where illuminance and time of day covary, include evaluation cases that distinguish them before attributing the prediction to illuminance.
- **Constrained forecasting.** Supply a candidate that the outdoor light will turn on when illuminance drops and train the model to estimate its likelihood or timing. Explicitly identify the experiment as constrained forecasting. Performance on the supplied candidate measures forecasting under that constraint and cannot by itself establish autonomous discovery.

## Expectation gate

A prediction may be considered by an expectation gate. The gate owns policy such as:

- how much model confidence is needed before committing;
- whether a new prediction should create an expectation;
- whether a later prediction is compatible with an existing expectation;
- whether a compatible prediction should refine the expected value, reinforce confidence, or both;
- whether an incompatible prediction should supersede the existing expectation;
- whether a prediction is sufficiently recent or otherwise eligible;
- what utility, cost or reward should be associated with creating, maintaining, fulfilling or violating an expectation;
- when an expectation stops being active and which result value should describe that outcome.

Compatibility is intentionally policy-level rather than simple value equality. For example, a temperature prediction moving from `21.0` to `21.2` may refine the same expectation, while a binary state changing from `true` to `false` may represent an incompatible belief.

The gate may also compare the current refined value with `expectationInitialValue`. If the belief has drifted too far from what was initially expected, policy may choose to supersede it rather than continually move the target. How much drift is acceptable, and any additional cost associated with superseding, remain policy decisions.

These rules are deliberately not encoded in `Prediction` or `Expectation`.

## Expectation

`Expectation<T>` represents a persistent belief after the expectation gate has decided a model prediction is worth committing to.

Unlike a `Prediction`, it is not a frozen snapshot of one model output. It records the belief state and stable reference points needed by core:

- the `Signal<T>` the belief concerns;
- `signalInitialValue`, the observed value of that same signal when the expectation was formed;
- `expectationInitialValue`, the initially expected value, equal to `value` when the expectation is created;
- `value`, the currently expected value;
- when the expectation was first formed;
- the current expectation confidence;
- an optional `ExpectationResult` once the expectation is no longer active.

Compatible later predictions may cause the gate to refine `value` and confidence while preserving `signalInitialValue`, `expectationInitialValue` and the original `formedAt`. This preserves both where reality started and what the belief originally committed to, which can later support visualization and policy-level evaluation without allowing gradual refinement to erase the original prediction.

`ExpectationResult` contains an open-ended string `value` and the `timestamp` when the expectation stopped being active. Core deliberately does not define a fixed result vocabulary. A subsystem may use values such as `Fulfilled`, `Abandoned`, `Superseded` or something domain-specific without changing the core type.

The lifecycle is therefore:

```mermaid
stateDiagram-v2
    [*] --> Active: expectation formed
    Active --> Active: compatible refinement
    Active --> Result: subsystem records result
    Result --> [*]
```

A null `result` means the expectation is still active. A non-null result closes its active interval at `result.timestamp`.

Core deliberately does not provide a built-in `reinforcedBy(...)` operation because deciding compatibility and update semantics is gate/policy behavior.

Cost, reward and other utility calculations are intentionally external. They depend on the subsystem, environment or policy evaluating the expectation rather than being intrinsic properties of the belief itself.

## Current boundary

The intended flow is:

```mermaid
flowchart LR
    M[Model] -->|Prediction value + confidence| G[Expectation gate]
    S[Current signal value] -->|signalInitialValue on creation| G
    G -->|create| E[Expectation]
    M -->|later prediction| G
    G -->|compatible: refine value / confidence| E
    G -->|incompatible or excessive drift: supersede| N[New expectation]
    G -->|close lifecycle| R[ExpectationResult]
    G -.->|compute utility externally| U[Policy / subsystem state]
```

The stable reference points are:

```mermaid
flowchart LR
    SI[signalInitialValue\nObserved signal at formation] --> E[Expectation]
    EI[expectationInitialValue\nInitial expected value] --> E
    E --> V[value\nCurrent refinable expected value]
```

This PR intentionally stops here. Violation detection, surprise, experience records, fulfillment evaluation, utility/reward delivery, lifecycle policy and replay policy should be introduced only when experiments establish useful semantics for them.
