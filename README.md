# chunk-clj

[![Clojars Project](https://img.shields.io/clojars/v/net.clojars.savya/chunk-clj.svg)](https://clojars.org/net.clojars.savya/chunk-clj)
[![cljdoc](https://cljdoc.org/badge/net.clojars.savya/chunk-clj)](https://cljdoc.org/d/net.clojars.savya/chunk-clj)
[![test](https://github.com/jsavyasachi/chunk-clj/actions/workflows/test.yml/badge.svg)](https://github.com/jsavyasachi/chunk-clj/actions/workflows/test.yml)

Recursive text splitting (chunking) for RAG and LLM pipelines - split text on natural
boundaries into overlapping chunks sized by characters **or tokens**.

## Stack

<a href="https://clojure.org"><img src="https://img.shields.io/badge/Clojure-5881D8?style=flat&logo=clojure&logoColor=white" alt="Clojure" /></a>
<a href="https://clojure.org/guides/deps_and_cli"><img src="https://img.shields.io/badge/deps.edn-5881D8?style=flat&logo=clojure&logoColor=fff" alt="deps.edn" /></a>
<a href="https://clojure.github.io/tools.build/"><img src="https://img.shields.io/badge/tools.build-5881D8?style=flat&logo=clojure&logoColor=fff" alt="tools.build" /></a>

The standard way to prepare documents for retrieval is to split them into chunks that
fit a model's context, on sensible boundaries, with a little overlap so a thought split
across two chunks survives in both. `chunk-clj` is the Clojure equivalent of LangChain's
`RecursiveCharacterTextSplitter`: it tries a list of separators from coarsest (paragraph)
to finest (character) until each chunk fits, then packs and overlaps them.

The size limit is whatever you want it to be - `:length-fn` defaults to characters, but
since models limit you by **tokens**, pass a token counter and chunk by real token
budgets.

## Install

tools.deps (`deps.edn`):

```clojure
net.clojars.savya/chunk-clj {:mvn/version "0.2.2"}
```

Leiningen (`project.clj`):

```clojure
[net.clojars.savya/chunk-clj "0.2.2"]
```

## Development

```shell
clojure -M:test
clojure -T:build jar
clojure -T:build deploy
```

## Usage

```clojure
(require '[chunk.core :as chunk])

;; Character-sized chunks with overlap (default):
(chunk/split long-text {:chunk-size 1000 :overlap 200})
;=> ["first ~1000-char chunk ..." "next chunk, sharing ~200 chars ..." ...]

;; Short text is returned whole:
(chunk/split "hello world" {:chunk-size 100})
;=> ["hello world"]

;; Custom separators (e.g. split markdown on headings first):
(chunk/split doc {:chunk-size 800 :separators ["\n## " "\n\n" "\n" " " ""]})

;; Or use a built-in language preset:
(chunk/split doc {:chunk-size 800 :language :markdown})

;; Keep source locations for indexing or highlighting:
(chunk/split-with-offsets doc {:chunk-size 800 :language :markdown})
;=> [{:text "...", :start 0, :end 42} ...]
```

Separators are kept by default with `:keep-separator :start`, attached to the piece
that follows them. This preserves language and markdown content such as `def ` and
`## `. Use `:keep-separator :end` to attach them to the preceding piece, or
`:keep-separator false` for the separator-dropping behavior from 0.2.2.

### Language presets

`:language` selects a separator preset from `chunk.core/language-separators` so
chunks land on structural boundaries (headings, function definitions, tags)
before falling back to paragraphs, lines, words, and characters. Available:
`:markdown`, `:python`, `:clojure`, `:javascript`, `:typescript`, `:java`,
`:go`, `:rust`, `:html`, `:latex`.

```clojure
(chunk/split source {:chunk-size 512 :language :clojure})

(chunk/separators-for :python)
;=> ["\nclass " "\ndef " "\n\tdef " "\n\n" "\n" " " ""]
```

All presets are literal strings (no regexes) ending in the default
paragraph/line/word/character tail. Passing both `:language` and `:separators`
throws; an unknown language keyword throws with the known set in `ex-data`.

### Chunk by tokens

Models cap input by tokens, not characters, so size chunks with a real tokenizer:

```clojure
(require '[chunk.core :as chunk]
         '[tokenizers.core :as tok])

(with-open [t (tok/from-pretrained "bert-base-uncased")]
  (chunk/split long-text {:chunk-size 256          ; 256 tokens, not chars
                          :overlap    32
                          :length-fn  #(tok/count-tokens t %)}))
```

Any `String -> number` function works as `:length-fn`, so you can target an embedding
model's exact token limit.

## Options

| Key | Default | Meaning |
|-----|---------|---------|
| `:chunk-size` | `1000` | Max chunk size, in `:length-fn` units |
| `:overlap` | `0` | Trailing context repeated at the start of the next chunk |
| `:separators` | `["\n\n" "\n" " " ""]` | Ordered split boundaries, coarsest first |
| `:keep-separator` | `:start` | Keep separators on the following piece (`:end` attaches them to the preceding piece; `false` drops them) |
| `:language` | - | Select a built-in language separator preset |
| `:length-fn` | `count` | Measures a string's size (swap in a token counter) |

An "atom" longer than `:chunk-size` with no admissible finer separator (e.g. one huge
word when `""` is not in `:separators`) is emitted whole rather than dropped.

`split-with-offsets` has the same options and returns `{:text s :start i :end j}` maps,
where offsets index the original input. With `:keep-separator false`, chunks that are
not exact source substrings have nil offsets. Token-mode measurements are cached per
split call, avoiding repeated tokenization of already-measured joined candidates.

## License

Copyright © 2026 Savyasachi

Distributed under the [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/).
