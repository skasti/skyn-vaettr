# Representation compatibility and Playpen migration

`Representation` is now the single backend-neutral interface in
`no.skasti.skynvaettr.representation`. The former numeric class is `NumericRepresentation`.
Its validation, numeric storage, reference equality, row order, indexing, iteration and list/array
copying behavior are preserved. `Embedding` is unchanged. This is an API refactoring; neural math,
training, prediction and topology scheduling are unchanged.

## Source and JVM compatibility

| Caller or behavior | Compatibility |
|---|---|
| Kotlin `Representation.of(...)` / `Representation.from(...)` | Retained companion factories return `Representation`, backed by `NumericRepresentation`. |
| `positions`, `dimensions`, `get`, `iterator`, `toList`, `Iterable<Embedding>` | Same instance signatures and numeric behavior. |
| `Port<Representation>`, projection/attention APIs, SampleEntryPoint, LearnableProcessingChain | Remain interface-oriented; existing source callers need no changes. |
| Callers needing the concrete numeric class | Use `NumericRepresentation.of/from`; these factories return `NumericRepresentation`. |
| Java source using `Representation.Companion.of/from` | Retained; no new `@JvmStatic` API is introduced. |
| Reflection or assumptions about the runtime class | Must account for an interface and the renamed implementation; class names and constructor reflection change. |
| Already compiled Kotlin/JVM or Java clients | **Binary incompatible: rebuild all dependent code against the new core.** |

The old JVM owner `Representation` was a class. Instance calls compiled against it use
`invokevirtual`; new clients call interface methods with `invokeinterface`. Keeping method names,
JVM descriptors and companion factory return types does not repair that class/interface change.
Rebuild dependent jars, including Playpen and any precompiled plugins, together; do not mix old and
new binaries. This is not claimed to be a binary-compatible release. No compatibility shim or
second core interface is added.

The interface specifies two positive axes and matching-width Embedding access while a value is
valid. It does not promise that every implementation has immutable live values, cheap numeric reads,
or unlimited lifetime. Those guarantees belong to implementations and a future ownership contract.
NumericRepresentation keeps the old immutable behavior; its `equals` remains reference identity,
not structural equality across backends.

## Evidence from Playpen

Reviewed baselines:

- [PR #30](https://github.com/skasti/vaettr-playpen/pull/30), head
  `18055f36d2b2e60d2c28941f8062a48dd38f6e16`, and
  [DJL findings](https://github.com/skasti/vaettr-playpen/blob/18055f36d2b2e60d2c28941f8062a48dd38f6e16/docs/djl-autograd-findings.md).
- [PR #31](https://github.com/skasti/vaettr-playpen/pull/31), head
  `07eae5eefe017005631c2ea25c683b521bc2dbfe`, its `TensorRepresentation`, `TensorNodes`,
  contract checks, and
  [interface findings](https://github.com/skasti/vaettr-playpen/blob/07eae5eefe017005631c2ea25c683b521bc2dbfe/docs/tensor-representation-findings.md).

Their reported results establish that real port delivery and backend borrowing can preserve tensor
identity and gradients. Numeric inspection need not destroy the original graph, but rebuilding a
tensor from Embeddings cuts it. Borrowed native state needs read-only aliases, a live owner and
completion before resource closure. These experiments do not establish asynchronous training or
unbounded persistent graphs. This core PR introduces no DJL dependency or backend implementation.

## Downstream migration

PR30's `Port<NDArray>` path stays independent of this refactoring. Its explicit numeric ingress and
negative-control `Representation.of/from` calls retain their source API after recompilation.

PR31's experimental `TensorRepresentation` has the same instance contract. Its existing prototype
can still compile separately: `NumericTensorRepresentation` delegates to the core interface using
unchanged methods. No Playpen source changes are required merely to rebuild against this core API.
It remains a nominally separate type until migrated; equal signatures do not make Kotlin types
interchangeable.

To adopt the core interface in a later Playpen change:

1. Replace the experimental interface and its usages/imports with core `Representation`. Have
   `DjlTensorRepresentation` implement it directly, retaining its backend accessor and validity rules.
2. Remove the now-unnecessary `NumericTensorRepresentation` adapter and pass numeric core values
   directly. Use `NumericRepresentation` only when a concrete numeric implementation is needed.
3. Change participating ports together to `Port<Representation>`. `Port<T>` is invariant, so
   `Port<NumericRepresentation>` and the old `Port<TensorRepresentation>` cannot substitute for it.
4. Keep explicit numeric-to-DJL ingress, DJL-only neural operations, mixed-context rejection and
   ownership/window checks. The common interface does not make core's numeric math differentiable.
5. Adjust any tests that require the old concrete class shape. The existing instance-signature
   reflection comparison remains valid. Rerun identity, gradient, negative-control, lifetime and
   matched-baseline checks.

No coordinated Playpen modification is part of this core refactoring. Migration should replace the
prototype abstraction, not establish a permanent second representation interface.


## Validation of this refactoring

- JDK 21 / Gradle 8.14: `gradle test` succeeds, 121 tests, zero failures/errors, two existing
  Graphviz-dependent rendering tests skipped locally because `dot` is unavailable. Repository CI
  installs Graphviz and runs the complete suite plus report rendering.
- Recompiled the unchanged PR30/PR31 neural sources and ran all nine tests in `DjlAutogradTest`
  (four) and `TensorRepresentationTest` (five), with zero failures or skips. This used a temporary
  composite build against the refactored core, Kotlin 2.1.21 and the experiments' pinned offline
  DJL 0.36.0 / PyTorch 2.7.1 CPU runtime. The five main files were `DjlNodes`, `DjlAutogradRig`,
  `TensorRepresentation`, `TensorNodes` and `TensorAutogradRig`; the six test files were the two
  test classes and their `DjlGradientChecks`, `ManualChainReference`, `TensorContractChecks` and
  `TensorGradientChecks` helpers, all unchanged from PR31's head above (which includes PR30).
  These checks cover port identity, numeric access, ownership/lifetime, finite differences, manual
  VJP agreement, fan-out, recurrence, inspection and graph-breaking negative controls. This was a
  focused downstream compatibility run, not the full Playpen suite or report-producing experiment.
- Compiled and ran the same Java source client against old core `a97bfec` and refactored core:
  companion factories, shape, indexing, iteration and `toList()` pass in both. Running the old
  compiled client against new core fails with the expected `IncompatibleClassChangeError`.
  `javap` confirms the preserved instance descriptors and changed class/interface invocation kind.

No downstream source modification was needed for those checks. Core's existing Kotlin callers,
examples/report sources and topology/attention/learning tests also compile against the interface.
