# chunk-clj

[![Clojars Project](https://img.shields.io/clojars/v/net.clojars.savya/chunk-clj.svg)](https://clojars.org/net.clojars.savya/chunk-clj)
[![cljdoc](https://cljdoc.org/badge/net.clojars.savya/chunk-clj)](https://cljdoc.org/d/net.clojars.savya/chunk-clj)
[![test](https://github.com/jsavyasachi/chunk-clj/actions/workflows/test.yml/badge.svg)](https://github.com/jsavyasachi/chunk-clj/actions/workflows/test.yml)

Recursive text splitting (chunking) for RAG and LLM pipelines - split text on natural
boundaries into overlapping chunks sized by characters **or tokens**. Pure Clojure, zero
dependencies.

## Stack

<a href="https://clojure.org"><img src="https://img.shields.io/badge/Clojure-5881D8?style=flat&logo=clojure&logoColor=white" alt="Clojure" /></a>

The standard way to prepare documents for retrieval is to split them into chunks that
fit a model's context, on sensible boundaries, with a little overlap so a thought split
across two chunks survives in both. `chunk-clj` is the Clojure equivalent of LangChain's
`RecursiveCharacterTextSplitter`: it tries a list of separators from coarsest (paragraph)
to finest (character) until each chunk fits, then packs and overlaps them.

The size limit is whatever you want it to be - `:length-fn` defaults to characters, but
since models limit you by **tokens**, pass a token counter (e.g.
[tokenizers-clj](https://github.com/jsavyasachi/tokenizers-clj)) and chunk by real token
budgets.

## Install

Leiningen / Boot:

```clojure
[net.clojars.savya/chunk-clj "0.1.0"]
```

deps.edn:

```clojure
net.clojars.savya/chunk-clj {:mvn/version "0.1.0"}
```

## Usage

```clojure
(require '[chunk.core :as chunk])

;; Character-sized chunks with overlap (the default):
(chunk/split long-text {:chunk-size 1000 :overlap 200})
;=> ["first ~1000-char chunk ..." "next chunk, sharing ~200 chars ..." ...]

;; Short text is returned whole:
(chunk/split "hello world" {:chunk-size 100})
;=> ["hello world"]

;; Custom separators (e.g. split markdown on headings first):
(chunk/split doc {:chunk-size 800 :separators ["\n## " "\n\n" "\n" " " ""]})
```

### Chunk by tokens (the right way)

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
| `:length-fn` | `count` | Measures a string's size (swap in a token counter) |

An "atom" longer than `:chunk-size` with no admissible finer separator (e.g. one huge
word when `""` is not in `:separators`) is emitted whole rather than dropped.

## License

Copyright © 2026 Savyasachi

Distributed under the [Eclipse Public License 2.0](https://www.eclipse.org/legal/epl-2.0/).
