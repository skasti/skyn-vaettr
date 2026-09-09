# Signal tokenization and vocabularies

Skynvættr separates **signal identity tokenization** from value encoding.

A `Signal<T>` identifies what can be sampled. A `Sample<T>` contains an observed value.
Tokenization therefore operates on signal identity, not on numeric sample values.

The core API intentionally stays source-agnostic:

- `SignalTokenizer` turns a `SignalId` into discrete `Token` values.
- `DelimitedSignalTokenizer` is a small baseline that splits common hierarchy and word separators.
- `VocabularyBuilder` builds a deterministic vocabulary from observed token frequencies.
- `Vocabulary` maps tokens to stable integer `TokenId` values.
- `<PAD>`, `<UNK>`, and `<MASK>` are reserved by default.

This is deliberately not a learned tokenizer such as BPE or SentencePiece yet. The playpen
should first establish which strategies preserve useful structure and generalize to unseen
signals before a stronger default is promoted into core.

A vocabulary is also intentionally not a mapping from complete signal IDs to arbitrary integers.
Shared fragments such as `sensor` and `temperature` remain visible so later embedding
experiments can exploit structure shared by related signals.
