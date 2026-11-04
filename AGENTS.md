# Repository guidance

Before proposing, implementing, or reviewing a learning architecture change, read the relevant repository documentation and check the design against its contracts.

- [Architecture](docs/architecture.md) defines the core principles and integration boundaries.
- [Predictions and expectations](docs/expectations.md) defines discovery, commitment, and evaluation responsibilities.
- [Learnable processing chain](docs/learnable-processing-chain.md) describes the opt-in trainable composition.
- [Processing topology](docs/topology.md) and [Default topology](docs/default-topology.md) describe the topology contract and reference composition.

For experiments, identify which decisions the model learns and which are supplied by preprocessing, training setup, or evaluation. Apply the discovery and evaluation guidance in the expectations document when assessing what the results demonstrate.

## Documentation describes the current architecture

Documentation in `docs/` must describe Skyn-Vættr as it currently exists, as though the architecture had been designed this way from the beginning.

- Describe current contracts, behavior, responsibilities, and limitations directly.
- Do not narrate implementation history, refactorings, migrations, or previous designs.
- Avoid temporal language such as "previously", "now", "new", "original implementation", or "after this change" when describing the architecture.
- Keep historical context, migration instructions, and compatibility notes in pull requests, changelogs, or dedicated migration documents when necessary.
- When changing an architectural contract, update the existing documentation to describe the resulting design coherently rather than appending a description of the change.
- Distinguish current functionality from planned functionality without describing the development history.
