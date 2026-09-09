# Architecture

This document describes the current conceptual architecture of Skynvættr.

Skynvættr is a runtime for persistent artificial entities. Each running Skynvættr instance represents such an entity: a continuously existing artificial system that perceives an environment, maintains evolving internal context over time, processes and reasons about what it senses, and can eventually affect that environment through explicitly defined mechanisms.

The architecture is intentionally domain-independent. Home automation, infrastructure monitoring, robotics, software systems, simulations, and other environments should be integrations built around the same core model rather than assumptions embedded into the runtime.

The first implementation is expected to evolve significantly. The concepts in this document are therefore intended to establish boundaries and vocabulary before the Kotlin API is allowed to solidify around implementation details.

## Architectural principles

The architecture is guided by a few core principles:

1. **Persistence over request/response**  
   A running Skynvættr instance exists across processing and perception cycles. It is not instantiated merely to answer one request and disappear.

2. **Signals are not perceptions**  
   Raw environmental data must remain distinct from the interpretation of that data.

3. **Internal representation is intentionally open**  
   Perception may contribute to persistent internal state or context, but Skynvættr does not yet prescribe whether that state should be represented as explicit beliefs, learned latent state, memories, structured facts, or some combination.

4. **Reasoning is a component, not the system**  
   An LLM, neural network, classifier, rules engine, or other model may participate in reasoning without defining the architecture as a whole.

5. **Sensing and acting are explicit boundaries**  
   The environment enters through signals and is affected through effectors. Integrations should not bypass these boundaries.

6. **Time is first-class**  
   History, rate of change, duration, ordering, freshness, and temporal relationships may all affect interpretation.

7. **Attention is selective**  
   Most environmental changes should not require expensive reasoning. Policies and lower-cost processing should determine what deserves further attention.

8. **Uncertainty is expected**  
   Different sources may disagree and observations may be incomplete. The architecture should leave room for uncertainty without prescribing how it must be represented.

9. **Subsystem cadence should be stable**  
   Individual sensing, perception, maintenance, and reasoning subsystems may each operate on their own cadence. These intervals should generally be explicit and stable rather than continuously adjusted as a proxy for attention.

10. **External constraints should remain external**  
    External services may impose their own operational constraints. These should be handled at the integration or resource boundary without coupling the cadence of internal subsystems directly to a particular provider.

## The continuous loop

At the highest level, a running Skynvættr instance participates in a continuous feedback loop with its environment.

```mermaid
flowchart TD
    ENV[Environment]
    SIG[Signals]
    PER[Perception]
    CTX[Internal context]
    PROC[Processing / Reasoning]
    BEH[Behaviour / Effectors]
    ACT[Effects on environment]

    ENV --> SIG
    SIG --> PER
    PER --> CTX
    CTX --> PROC
    PROC --> BEH
    BEH --> ACT
    ACT --> ENV

    CTX --> PER
    CTX --> PROC
```

This diagram is intentionally circular. The system does not process one isolated input to produce one isolated output. Each cycle takes place in the context produced by previous cycles.

## Core interaction loop

The current architecture deliberately keeps the cognitive path broad.

```text
Environment -> Sensing -> Processing / internal state -> Behaviour -> Environment
```

```mermaid
flowchart LR
    E[Environment]
    S[Sensing]
    P[Processing / internal state]
    B[Behaviour]

    E --> S
    S --> P
    P --> B
    B --> E
```

This is intentionally less specific than a pipeline of named cognitive objects. Skynvættr should establish the boundaries required for persistent sensing, processing, learning, and interaction without prematurely deciding how goals, expectations, plans, decisions, or intentions must be represented internally.

### Signal

A **Signal** defines one uniquely named value that a Skynvættr can receive from its environment.

For example, an integration might define:

```text
sensor.kontor_presence_temperature
```

with metadata such as:

```text
state_class = measurement
unit_of_measurement = °C
device_class = temperature
friendly_name = Kontor Presence Temperature
```

The signal definition describes *what can be sampled*. It does not itself represent a value received at a particular point in time.

The core deliberately keeps signal metadata open-ended rather than hard-coding semantic classifications such as channels. Integrations and later processing layers may attach whatever metadata is useful for their domain.

### Sample

A **Sample** is one value received for a Signal at a specific point in time.

```text
signal = sensor.kontor_presence_temperature
value = 25.9
timestamp = 2026-09-09T11:00:00Z
```

A Sample is intentionally temporally neutral. It records what was received and when, but does not imply that the value remains the current state until another sample arrives. Stateful, event-like, or impulse semantics may be introduced later if experiments show that the runtime needs to distinguish them explicitly.

### Observation

An **Observation** is the result of perception: an interpretation that is meaningful to the entity.

A raw signal such as:

```text
temperature = 29.1 C
```

might contribute to an observation such as:

```text
The temperature has risen unusually quickly during the last 20 minutes.
```

Observations may be derived from one signal, many signals, historical state, existing beliefs, or combinations of these.

### Internal state / context

Perception may update persistent internal state used by later processing and decisions.

The runtime should not yet prescribe the form of that state. Candidate approaches may include:

- structured facts or relationships;
- explicit hypotheses or beliefs with confidence;
- episodic or semantic memory;
- learned latent state;
- model-specific context;
- combinations of several approaches.

These alternatives should be explored experimentally before one is promoted to a core abstraction.


### Behaviour and action

Skynvættr is intended to eventually affect its environment, but the architecture does not yet prescribe a specific cognitive object such as an `Intent`.

A long-term goal is for Skynvættr to be able to communicate or otherwise represent what it is trying to achieve well enough that behaviour can be selected, outcomes can be observed, expectations can be evaluated, and future behaviour can adapt or learn from the result.

Whether concepts such as intent, goal, plan, expectation, or action become explicit core abstractions should be determined through experiments rather than fixed now.

## Signals, samples, entities, and sources

The signal model should preserve stable signal identity while allowing integrations to describe signals through open-ended metadata.

A tentative model is:

```mermaid
classDiagram
    class Environment
    class Source
    class Entity
    class Signal
    class Sample

    Environment "1" o-- "*" Source
    Environment "1" o-- "*" Entity
    Source "1" --> "*" Signal : defines
    Signal "1" --> "*" Sample : sampled as
```

### Source

A **Source** is something capable of producing signals.

Examples include:

- a physical sensor;
- a Home Assistant integration;
- a camera perception model;
- an operating-system telemetry collector;
- an API;
- an event stream;
- another external system.

### Entity

An **Entity** is something in the observed world about which the system can maintain state or context.

Within the runtime model, `Entity` refers to something in the environment that a Skynvættr instance can observe or maintain state about. The broader description of a running Skynvættr instance as an artificial entity is descriptive terminology, not a separate runtime type.

Examples include a person, animal, room, machine, application, service, vehicle, or other meaningful object.

### Signal metadata

Semantic information about a signal should initially be represented as metadata rather than as a fixed `Channel` abstraction.

Examples may include units, device classes, source information, human-readable names, ranges, quality descriptors, or integration-specific classifications. This keeps the core domain-independent and allows later experiments to determine which concepts, if any, deserve promotion to first-class abstractions.

### Signal history and current state

The runtime should retain both current values and historical signals where appropriate.

Historical information enables interpretation of:

- rate of change;
- duration;
- repeated patterns;
- freshness;
- transitions;
- correlations;
- temporal sequences.

A current value alone is often insufficient for useful perception.

### Learned signal representations

Signals should remain semantically meaningful, inspectable records of what was sensed. Models may, however, derive **learned representations** from signals in order to discover useful relationships that are difficult or undesirable to encode manually.

This is analogous to learned token embeddings in language models, but sensor data should not be forced into a text-like token model. In particular, many sensor values are continuous and temporal relationships are often more important than the isolated value itself.

A model may therefore encode a signal using some combination of:

- signal identity and metadata;
- source;
- associated entity;
- value or event payload;
- timestamp or temporal encoding;
- confidence, reliability, quality, and other metadata;
- surrounding signal history or model-specific context.

Conceptually:

```text
Signal + context -> encoder -> learned representation
```

For a single signal, an encoder might produce a vector representation:

```text
temperature(room, 22.4 C, time=t, ...)
        |
        v
      encoder
        |
        v
[ learned latent representation ]
```

For continuous values, the default assumption should be that models can encode the value directly rather than requiring arbitrary discretization into token-like buckets. Discretization may still be useful for particular models or experiments, but it is a modelling decision rather than a property of the signal itself.

#### Temporal representations

Many useful properties of sensed environments are expressed by change over time rather than by individual readings.

For example:

```text
21 -> 22 -> 23 -> 24 -> 25 C over 20 minutes
```

contains information that is not represented by the final value alone.

Models should therefore be able to encode **time windows or sequences of signals** into learned representations as well as individual signals:

```mermaid
flowchart LR
    H[Signal history / time window]
    E[Signal / temporal encoder]
    Z[Learned representation]
    M[Perception or prediction model]

    H --> E
    E --> Z
    Z --> M
```

Such a representation may capture correlations between signals, entities, rates of change, recurring situations, or other latent structure without requiring those concepts to be predefined as explicit fields.

The architecture must not assign fixed human meanings to individual latent dimensions. A vector whose dimensions are manually defined as temperature, humidity, presence, and so on is a useful feature vector, but it is not the same thing as a learned latent representation. Learned representations should remain free to organize information according to what is useful for the training objective.

#### Raw signals remain canonical

Learned representations are **derived model artifacts**, not replacements for signal history.

The runtime should preserve the underlying signals and enough metadata to reconstruct the model input independently of any particular encoder. This is important because the meaning of a learned representation may change when:

- an encoder is retrained;
- model architecture changes;
- input normalization changes;
- available channels change;
- a new training objective produces a better representation.

Persisted embeddings or latent states should therefore identify the model and representation version that produced them when they are stored at all.

Conceptually:

```text
Signal history                    model-specific artifacts
      |                                     |
      +----> encoder v1 ----> embedding v1  |
      |                                     |
      +----> encoder v2 ----> embedding v2  |
      |                                     |
      +----> other model -------------------+
```

This separation allows future models to reinterpret historical experience without losing information through an earlier representation choice.

#### Learning from experience

The signal history also provides a natural basis for self-supervised and other forms of representation learning.

Useful training objectives may include, for example:

- predicting future signals from a preceding time window;
- reconstructing masked or missing signals;
- predicting one signal from correlated signals;
- distinguishing similar and dissimilar situations;
- learning representations that are useful for downstream perception, prediction, or control tasks.

These are examples rather than prescribed objectives. The important architectural property is that training can sample historical experience independently of how signals are stored.

When episode-based training support is introduced, an episode should describe the experience to be sampled rather than replace the underlying signal history. An episode may identify a model instance, a time interval, relevant signals, and model-specific metadata. A trainer can then select episodes and resolve their actual inputs from the canonical signal history.

```mermaid
flowchart LR
    ES[Episode metadata]
    SH[Signal history]
    T[Trainer]
    ENC[Encoder / model]
    LOSS[Training objective]

    ES --> T
    SH --> T
    T --> ENC
    ENC --> LOSS
```

This keeps experience selection, signal storage, learned representation, and training strategy separate. It also allows several models to learn from the same recorded experience using different encoders or objectives.

## Perception

The perception layer turns raw signals and context into observations.

```mermaid
flowchart TD
    IN[Incoming signals]
    STATE[Current state and history]
    POLICY[Signal policies]
    CHANGE[Change / pattern detection]
    REL[Relevance and significance]
    OBS[Observations]
    TRIGGER[Perception trigger / attention update]

    IN --> POLICY
    STATE --> POLICY
    POLICY --> CHANGE
    CHANGE --> REL
    REL --> OBS
    REL --> TRIGGER
```

Perception is not required to involve an LLM. Lower-cost deterministic or learned components should handle routine processing whenever possible.

### Signal policies

Signal policies are the first line of interpretation and filtering.

A policy may decide to:

```mermaid
flowchart LR
    S[Signal]
    P{Signal policy}
    IGN[Ignore]
    STATE[Update state only]
    OBS[Create observation]
    WAKE[Trigger immediate perception]

    S --> P
    P --> IGN
    P --> STATE
    P --> OBS
    P --> WAKE
```

Policies may consider:

- absolute thresholds;
- rate of change;
- elapsed time;
- source reliability;
- signal confidence;
- disagreement between sources;
- current context;
- combinations of signals;
- expected versus actual state.

This allows most uninteresting changes to be processed without invoking higher-cost reasoning.

## Scheduling, cadence, and resource budgets

Skynvættr should not depend on one global perception interval.

Different subsystems may have different natural cadences. A temperature source might be sampled periodically, a local classifier may run frequently, a maintenance process may run much less often, and an event-driven source may have no polling interval at all.

Where an interval is appropriate, the default model is that it belongs to the individual subsystem and remains as stable as practical.

```mermaid
flowchart LR
    subgraph RUNTIME[Skynvættr runtime]
        S1[Sensor subsystem<br/>stable cadence A]
        S2[Perception subsystem<br/>stable cadence B]
        S3[Maintenance subsystem<br/>stable cadence C]
        EV[Event-driven subsystem<br/>on signal]
    end

    S1 --> BUS[Signals / observations]
    S2 --> BUS
    S3 --> BUS
    EV --> BUS
```

This differs from earlier prototype approaches where a comparatively global perception interval could be adjusted to make the system "wake up" more or less frequently. Skynvættr should instead let each subsystem express its own timing requirements.

Attention still matters, but it should primarily affect **priority and allocation of processing resources**, not continuously rewrite subsystem intervals.

For example, attention may influence:

- which pending observations are processed first;
- which histories or contextual data are loaded;
- which reasoning component is selected;
- how much inference budget is allocated;
- whether an unresolved question deserves an external model call;
- which tasks may consume a constrained external resource.

### External service constraints

External services may impose limits or temporary availability constraints of their own. Skynvættr should treat these as properties of the integration boundary rather than as timing rules for the entity itself.

A subsystem should therefore be able to retain its own cadence even when an external dependency cannot immediately satisfy a request. The exact mechanisms for handling such constraints are intentionally left to later design.


## Persistent internal context

A running Skynvættr instance needs some form of persistent internal context across processing cycles. This allows later perception and decisions to depend on what has happened before rather than only on the latest signal.

The architecture intentionally does not yet define this as a particular "world model" structure.

```mermaid
flowchart LR
    H[History / prior state]
    P[Perception]
    C[Persistent internal context]
    D[Decision]
    N[New signals]

    H --> C
    N --> P
    C --> P
    P --> C
    C --> D
```

Possible representations include structured state, memory, explicit beliefs, learned latent representations, or mixtures of these. Which abstractions deserve to become part of the core API should be determined through experiments and measured behavior rather than assumed up front.

## Internal state and drives

A running Skynvættr instance may need internal state that is neither a representation of the environment nor an externally supplied goal.

Examples might include:

- uncertainty;
- urgency;
- curiosity;
- attention;
- confidence;
- unresolved questions;
- goals;
- impulses or other internal drives.

These concepts should not require anthropomorphic interpretation. They may simply be structured values that influence prioritization and reasoning.

```mermaid
flowchart LR
    C[Internal context]
    D[Drives / Internal state]
    ATT[Attention]
    R[Reasoning]
    B[Behaviour]

    C --> ATT
    D --> ATT
    ATT --> R
    C --> R
    D --> R
    R --> B
```

For example, high uncertainty about an important belief may cause the system to increase attention toward signals capable of resolving that uncertainty.

## Reasoning and minds

Reasoning is a subsystem within a running Skynvættr instance, not the whole entity itself.

A Skynvættr instance may use multiple reasoning components.

```mermaid
flowchart TD
    ORCH[Reasoning orchestration]
    LLM[General-purpose LLM]
    SMALL[Small language model]
    CLS[Classifier]
    TS[Time-series model]
    RULES[Rule engine]
    VISION[Vision model]

    ORCH --> LLM
    ORCH --> SMALL
    ORCH --> CLS
    ORCH --> TS
    ORCH --> RULES
    ORCH --> VISION
```

The runtime should therefore avoid assuming that reasoning means making one call to one LLM provider.

Different components may specialize in:

- interpreting observations;
- anomaly detection;
- temporal prediction;
- visual perception;
- planning;
- language interaction;
- safety validation;
- domain-specific inference.

A higher-level "mind" abstraction may eventually coordinate these components, but that terminology remains provisional.

## Behaviour, capabilities, and effectors

The action side should remain explicit and general without prescribing where behaviour originates internally.

```mermaid
flowchart LR
    P[Internal processing]
    B[Behaviour / requested outcome]
    C[Capability]
    E[Effector]
    A[Action]
    ENV[Environment]

    P --> B
    B --> C
    C --> E
    E --> A
    A --> ENV
```

The node labelled `Behaviour / requested outcome` is intentionally representation-neutral. It may eventually correspond to intent, a goal, a plan, a policy result, a learned action proposal, or something produced by a different branch of the system entirely.

Skynvættr should not require an explicit `Intent` object merely to connect internal processing to action.

### Capability

A **Capability** describes something a Skynvættr instance is allowed and able to do in semantic terms.

Examples:

- change room temperature;
- send a message;
- move a robotic actuator;
- restart a software service;
- request additional information.

Capabilities provide a boundary where availability, policy, permissions, and safety constraints can be expressed.

### Effector

An **Effector** is an environment-specific mechanism capable of carrying out actions.

Examples might include:

- Home Assistant;
- MQTT;
- Kubernetes;
- a robot controller;
- a shell or operating-system integration;
- an external API.

Internal processing should not need to know the implementation details of these integrations.

### Example

```mermaid
sequenceDiagram
    participant P as Internal processing
    participant C as Capability layer
    participant E as Home Assistant effector
    participant W as Environment

    P->>C: Request an environment change
    C->>C: Validate capability and policy
    C->>E: Perform environment-specific operation
    E->>W: Execute action
    W-->>E: Environment changes / outcome
```

The consequences of actions should remain observable so that later processing can evaluate what happened and adapt. The architecture does not yet prescribe the exact feedback representation or route.

## Integration boundary

Skynvættr should not embed assumptions about a particular environment.

```mermaid
flowchart TB
    subgraph CORE[Skynvættr runtime]
        SIGNALS[Signals]
        PER[Perception]
        CONTEXT[Internal context]
        REASON[Reasoning]
        EFFECT[Capabilities / Effectors]
    end

    HA[Home Assistant]
    MQTT[MQTT]
    API[External APIs]
    ROBOT[Robotics]
    INFRA[Infrastructure]
    SIM[Simulation]

    HA --> SIGNALS
    MQTT --> SIGNALS
    API --> SIGNALS
    ROBOT --> SIGNALS
    INFRA --> SIGNALS
    SIM --> SIGNALS

    EFFECT --> HA
    EFFECT --> MQTT
    EFFECT --> API
    EFFECT --> ROBOT
    EFFECT --> INFRA
    EFFECT --> SIM

    SIGNALS --> PER
    PER --> CONTEXT
    CONTEXT --> REASON
    REASON --> EFFECT
```

Home Assistant may be an important early integration because it provides a rich environment for experimentation, but it must remain an adapter around the core model.

## Lessons from earlier experiments

Several architectural ideas were explored in earlier experiments and prototypes and motivate the current Skynvættr model.

These include:

- structured signal policies;
- weighting and filtering observations using dimensions such as importance, reliability, significance, and confidence;
- persistent historical and contextual state;
- adaptive perception intervals as an experimental mechanism, with the lesson that Skynvættr should instead give individual subsystems their own mostly stable cadences while keeping external service constraints separate from those internal rhythms;
- stored thoughts and unresolved questions;
- internal impulses and mood-like state;
- multiple specialized reasoning components or "subminds";
- explicit tools for affecting the environment.

Skynvættr should preserve the useful concepts discovered through those experiments while removing assumptions that belonged specifically to the prototype implementations, orchestration tools, environment integrations, or any particular model provider.

## Tentative runtime decomposition

The implementation should remain free to evolve, but the conceptual boundaries suggest a decomposition similar to:

```text
skyn-vaettr
|
+-- core
|   +-- entities
|   +-- signals
|   +-- channels
|   +-- observations
|   +-- time
|
+-- perception
|   +-- policies
|   +-- change-detection
|   +-- attention
|   +-- pipeline
|
+-- world
|   +-- state
|   +-- history
|   +-- memory
|
+-- mind
|   +-- reasoning
|   +-- models
|   +-- orchestration
|   +-- drives
|
+-- actions
|   +-- capabilities
|   +-- effectors
|
+-- integrations
    +-- ...
```

This is not a prescribed package structure. Creating empty modules merely to mirror this diagram would be premature. The value of the decomposition is to keep responsibilities separate as concrete types emerge.

## Early implementation focus

The initial milestones should concentrate on the parts that establish the semantic foundation:

1. signals, samples, and signal metadata;
2. current and historical signal state;
3. signal policies;
4. observation generation;
5. a perception pipeline;
6. a minimal persistent internal context representation.

Reasoning orchestration, richer belief revision, internal drives, and effectors can be introduced once those foundations have proven useful.

This sequence deliberately avoids starting with an LLM abstraction. Doing so would risk making the reasoning implementation define the architecture instead of fitting into it.

## Open architectural questions

Several concepts remain intentionally unresolved:

- What is the exact ownership relationship between sources, entities, and signals?
- Should observations be immutable event records?
- Which internal-state representations prove useful enough to become core abstractions?
- Which parts of internal context should be durable across restarts?
- How should temporal relationships be represented?
- Is attention a first-class object, a policy result, or both?
- Should "mind" become a concrete abstraction or remain descriptive terminology?
- How should reasoning components declare the context they require?
- How should capabilities express permissions, risk, and reversibility?
- Should intent become an explicit runtime abstraction at all, and if so where should it originate?
- How should a Skynvættr instance distinguish externally assigned goals from internally generated drives?
- How should multiple Skynvættr instances communicate or share information, if that becomes useful?

These questions should be answered through implementation and experiments rather than prematurely fixed in the public API.

## Summary

The central architectural idea is that a running Skynvættr instance is the complete persistent loop:

```mermaid
flowchart LR
    S[Sensing]
    P[Perception]
    W[Understanding]
    R[Reasoning]
    A[Action]

    S --> P --> W --> R --> A --> S
```

An LLM is not Skynvættr. Home Assistant is not Skynvættr. A memory store is not Skynvættr.

They may all participate in a Skynvættr instance.

The Skynvættr runtime brings sensing, perception, persistent internal state, reasoning, and action together so that each running instance forms one continuously existing artificial entity while keeping those concerns separable and composable.
