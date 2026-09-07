# Consequence-driven learning flow

This document describes the minimal learning boundary introduced by the consequence-learning experiment.

Skynvættr core does **not** define what a sensor means, what an effector does, or which environmental state is desirable. It only provides a neutral structure for observations, actions, outcomes, and learning feedback.

## Core interaction

```mermaid
flowchart LR
    ENV[Environment]
    SENSORS[Ordinary sensor values]
    FRAME[SensoryFrame]
    POLICY[AdaptivePolicy]
    ACTION[EffectorValue]
    EFFECTOR[Effector]
    RESULT[Environment changes]
    CONSEQ[Consequence]
    EXP[Experience]

    ENV --> SENSORS
    SENSORS --> FRAME
    FRAME --> POLICY
    POLICY --> ACTION
    ACTION --> EFFECTOR
    EFFECTOR --> RESULT
    RESULT --> ENV

    FRAME --> EXP
    ACTION --> EXP
    RESULT --> EXP
    CONSEQ --> EXP
    EXP --> POLICY
```

The important distinction is that `Consequence` does not describe a correct action. It is only global feedback about whether an experienced outcome should be reinforced or discouraged.

## Time and delayed consequences

A consequence observed now may have been caused by actions from several earlier cycles.

```mermaid
flowchart LR
    A0[Action t0]
    A1[Action t1]
    A2[Action t2]
    A3[Action t3]
    C4[Consequence t4]

    A0 -. possible influence .-> C4
    A1 -. possible influence .-> C4
    A2 -. possible influence .-> C4
    A3 -. possible influence .-> C4
```

Core deliberately does not prescribe how temporal credit assignment works. A learner may use eligibility traces, recurrent state, a learned world model, replay, or another approach.

## Architectural boundary

```mermaid
flowchart TB
    subgraph CORE[Skynvættr core]
        SF[SensoryFrame]
        EV[EffectorValue]
        C[Consequence]
        X[Experience]
        AP[AdaptivePolicy boundary]
    end

    subgraph EXPERIMENT[Experimental implementation]
        NN[Neural network]
        TRACE[Temporal credit assignment]
        TRAIN[Learning algorithm]
    end

    subgraph WORLD[Environment / integration]
        SENSOR_IMPL[Sensor semantics]
        EFFECT_IMPL[Effector physics / delay]
        CONSEQ_IMPL[How consequences arise]
    end

    SENSOR_IMPL --> SF
    SF --> AP
    AP --> EV
    EV --> EFFECT_IMPL
    EFFECT_IMPL --> CONSEQ_IMPL
    CONSEQ_IMPL --> C
    SF --> X
    EV --> X
    C --> X
    X --> AP

    NN --> AP
    TRACE --> TRAIN
    TRAIN --> NN
```

This keeps domain-specific meaning outside the core while still giving experiments a common feedback boundary.
