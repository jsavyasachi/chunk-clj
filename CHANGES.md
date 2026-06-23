# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/), and the project adheres to
[Semantic Versioning](https://semver.org/).

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
