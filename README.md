# Skynvættr

**Skynvættr** (pronounced roughly **“SKÜN-vettr”**) is a runtime for persistent artificial entities that perceive their environment through structured signals, build contextual understanding over time, and act through extensible mechanisms.

The name combines Old Norse *skyn* — sense, perception, understanding — with *vættr* — a being or entity.

> **Status:** Early-stage and experimental. The architecture and terminology are expected to evolve as the first implementation takes shape.

## Idea

Most AI systems are invoked to answer a request and then disappear. Skynvættr explores a different model: an artificial entity that exists continuously, receives observations from an evolving environment, maintains context across time, and can eventually influence that environment through explicitly defined mechanisms.

The core idea is intentionally domain-independent. A signal could originate from a physical sensor, an application, infrastructure telemetry, an event stream, an API, or any other observable source.

A simplified conceptual loop looks like this:

```text
signals
   ↓
perception
   ↓
context / world model
   ↓
reasoning and decisions
   ↓
actions / effectors
   ↓
environment
   ↺
```

## Design goals

Skynvættr aims to provide a foundation for systems that can:

- ingest heterogeneous signals through well-defined channels;
- preserve signal history and current state;
- distinguish meaningful changes from noise;
- maintain persistent contextual understanding across perception cycles;
- reason about relationships, sequences, uncertainty, and significance;
- remain independent of any specific physical environment or integration platform;
- expose extensible mechanisms for taking actions in the environment.

The project is not intended to be tied to smart homes, Home Assistant, a particular LLM provider, or even necessarily to LLM-based reasoning. Those may be integrations or implementations built on top of the core model.

## Terminology

Some terminology is still provisional, but the current model includes concepts such as:

- **Signal** — an observable value or state received from the environment.
- **Channel** — a defined semantic stream within an entity or source, such as state, position, temperature, connectivity, or another attribute.
- **Perception** — the process of interpreting incoming signals and changes in context.
- **World model / context** — the entity's evolving internal representation of what it believes is happening.
- **Effector** — a mechanism through which the entity can affect its environment.

These concepts are expected to become more precise as the implementation develops.

## Implementation

The initial implementation is being developed in **Kotlin**.

The first milestones are expected to focus on signal ingestion, channel definitions, current and historical signal storage, signal policies, and a perception pipeline. Action mechanisms will be defined later.

## License

Skynvættr is licensed under the **PolyForm Noncommercial License 1.0.0**. See [LICENSE.md](LICENSE.md).

Noncommercial use — including personal study, experimentation, hobby projects, and other uses permitted by the license — is allowed.

**Commercial use is not granted by the public license.** If you want to use Skynvættr commercially, a separate commercial license is required from the copyright holder.

This project is therefore source-available, but it is not Open Source under the OSI definition because the public license restricts commercial use.
