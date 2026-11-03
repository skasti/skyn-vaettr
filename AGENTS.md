# Repository guidance

Before proposing, implementing, or reviewing a learning architecture change, read the relevant repository documentation and check the design against its contracts.

- [Architecture](docs/architecture.md) defines the core principles and integration boundaries.
- [Predictions and expectations](docs/expectations.md) defines discovery, commitment, and evaluation responsibilities.
- [Learnable processing chain](docs/learnable-processing-chain.md) describes the opt-in trainable composition.
- [Processing topology](docs/topology.md) and [Default topology](docs/default-topology.md) describe the topology contract and reference composition.

For experiments, identify which decisions the model learns and which are supplied by preprocessing, training setup, or evaluation. Apply the discovery and evaluation guidance in the expectations document when assessing what the results demonstrate.
