# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/), and the project adheres to
[Semantic Versioning](https://semver.org/).

## [0.2.0] - 2026-07-07

### Added
- `chunk.core/language-separators` - separator presets for `:markdown`,
  `:python`, `:clojure`, `:javascript`, `:typescript`, `:java`, `:go`, `:rust`,
  `:html`, and `:latex`, so chunks land on structural boundaries (headings,
  function definitions, tags) before the paragraph/line/word/character tail.
- `chunk.core/separators-for` - preset lookup; unknown language throws with the
  known set in `ex-data`.
- `:language` option on `split` - sugar for `:separators (separators-for lang)`;
  passing both `:language` and `:separators` throws.

## [0.1.1] - 2026-06-29

License metadata release (EPL 2.0 in the POM); no code changes.

## [0.1.0] - 2026-06-23

Initial release.

### Added
- `chunk.core/split` - recursive text splitter for RAG / LLM pipelines. Splits text
  into overlapping chunks no larger than a target size, trying ordered separators
  (paragraph -> line -> word -> character) so chunks land on natural boundaries.
- Options: `:chunk-size`, `:overlap`, `:separators`, and a pluggable `:length-fn`
  (default `count` = characters) so you can chunk by **tokens** instead - e.g. with
  tokenizers-clj's `count-tokens`.
- Oversized atoms with no admissible finer separator are emitted whole, never dropped.
- Zero runtime dependencies.

[0.1.0]: https://github.com/jsavyasachi/chunk-clj/releases/tag/0.1.0
