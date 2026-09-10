# Signal tokenization and token representations

Skynvættr separates **signal identity tokenization** from value encoding and from the numeric representation of lexical tokens.

A `Signal<T>` identifies what can be sampled. A `Sample<T>` contains an observed value. Tokenization therefore operates on signal identity, not on numeric sample values.

The core API intentionally stays source-agnostic:

- `SignalTokenizer` turns a `SignalId` into discrete `Token` values.
- `DelimitedSignalTokenizer` is a small baseline that splits common hierarchy and word separators.
- `VocabularyBuilder` builds a deterministic vocabulary from observed token frequencies.
- `Vocabulary` maps tokens to stable integer `TokenId` values when a vocabulary-based model needs that representation.
- `TokenEncoder` independently maps one lexical `Token` to one fixed-width numeric vector.
- `DeterministicByteTokenEncoder` is a vocabulary-free compositional baseline for both known and previously unseen lexical tokens.
- `<PAD>`, `<UNK>`, and `<MASK>` remain reserved for vocabulary-based use cases.

## Why tokenization and representation are separate

A tokenizer answers **which lexical units are present**. A token encoder answers **how one lexical unit is represented numerically**.

For example:

```text
sensor.kontor_presence_temperature
              |
              v
[ sensor, kontor, presence, temperature ]
              |
              v
     one fixed-width vector per token
```

Keeping these stages separate avoids making a vocabulary lookup the only possible representation. A model may still use `Vocabulary` and `TokenId`, but it may instead use a compositional encoder that can represent a token that did not exist when the model was created.

## Deterministic byte composition

`DeterministicByteTokenEncoder` projects UTF-8 byte unigrams and adjacent byte bigrams into a fixed-width vector and L2-normalizes the result.

This baseline has a few useful properties:

- one outer sequence position remains one lexical token;
- the representation width is fixed;
- unseen token values do not all become `<UNK>`;
- no vocabulary or mutable model state is required;
- the algorithm is source-agnostic and does not depend on Home Assistant naming conventions.

It should **not** be interpreted as a semantic embedding. Its purpose is to preserve lexical identity and compositional spelling structure while later learned encoders are explored.

## What remains experimental

Playpen experiments now provide enough evidence to promote the separation between tokenization and fixed-width token representation, together with deterministic byte composition as a baseline.

They do **not** yet justify promoting a particular learned byte n-gram objective into core. Learned contrastive n-grams show useful neighbourhood structure on unseen tokens, and known-only centering strongly suggests that their high raw cosine similarity is caused by a global common component rather than complete representation collapse. However, deterministic byte composition is still competitive or better on several purely lexical probes, and the learned encoder has not yet demonstrated enough semantic/contextual value to justify its additional training complexity as a core default.

Centering and learned token encoders therefore remain playpen concerns for now. `TokenEncoder` is intentionally general enough for those implementations to be added later without changing the tokenizer API.

A vocabulary is also intentionally not a mapping from complete signal IDs to arbitrary integers. Shared fragments such as `sensor` and `temperature` remain visible so later representation-learning experiments can exploit structure shared by related signals.
