(ns chunk.core
  "Recursive text splitting (chunking) for RAG / LLM pipelines.

  `split` breaks text into overlapping chunks no larger than a target size, trying an
  ordered list of separators from coarsest (paragraph) to finest (character) so chunks
  land on natural boundaries. Size is measured by `:length-fn` (default `count` =
  characters); pass a token counter (e.g. tokenizers-clj's `count-tokens`) to chunk by
  tokens instead - the correct unit when feeding a model with a token limit."
  (:require [clojure.string :as str])
  (:import [java.util.regex Pattern]))

(def default-separators
  "Coarsest-to-finest split boundaries (the empty string splits into characters)."
  ["\n\n" "\n" " " ""])

(def language-separators
  "Language-aware split boundaries, coarsest first."
  {:markdown (into ["\n# " "\n## " "\n### " "\n#### " "\n##### " "\n###### "
                   "```\n"
                   "\n\n***\n\n" "\n\n---\n\n" "\n\n___\n\n"]
                  default-separators)
   :python (into ["\nclass " "\ndef " "\n\tdef "] default-separators)
   :clojure (into ["\n(defn " "\n(def " "\n(defmacro " "\n(defmulti "
                   "\n(defmethod " "\n(defprotocol " "\n(defrecord "
                   "\n(deftest " "\n(ns "]
                  default-separators)
   :javascript (into ["\nfunction " "\nconst " "\nlet " "\nvar " "\nclass "
                      "\nif " "\nfor " "\nwhile " "\nswitch " "\ncase "
                      "\ndefault "]
                     default-separators)
   :typescript (into ["\nfunction " "\nconst " "\nlet " "\nvar "
                      "\ninterface " "\nenum " "\ntype " "\nnamespace "
                      "\nclass " "\nif " "\nfor " "\nwhile " "\nswitch "
                      "\ncase " "\ndefault "]
                     default-separators)
   :java (into ["\nclass " "\npublic " "\nprotected " "\nprivate " "\nstatic "
                "\nif " "\nfor " "\nwhile " "\nswitch " "\ncase "]
               default-separators)
   :go (into ["\nfunc " "\nvar " "\nconst " "\ntype " "\nif " "\nfor "
              "\nswitch " "\ncase "]
             default-separators)
   :rust (into ["\nfn " "\nconst " "\nlet " "\nif " "\nwhile " "\nfor "
                "\nloop " "\nmatch "]
               default-separators)
   :html (into ["<body" "<div" "<p" "<br" "<li" "<h1" "<h2" "<h3" "<h4"
                "<h5" "<h6" "<span" "<table" "<tr" "<td" "<th" "<ul" "<ol"
                "<header" "<footer" "<nav" "<head" "<style" "<script"
                "<meta" "<title"]
               default-separators)
   :latex (into ["\n\\chapter{" "\n\\section{" "\n\\subsection{"
                 "\n\\subsubsection{" "\n\\begin{enumerate}"
                 "\n\\begin{itemize}" "\n\\begin{description}"
                 "\n\\begin{list}" "\n\\begin{quote}" "\n\\begin{quotation}"
                 "\n\\begin{verse}" "\n\\begin{verbatim}" "\n\\begin{align}"]
                default-separators)})

(defn separators-for
  "Return the separator vector for language keyword `lang`."
  [lang]
  (if-let [separators (get language-separators lang)]
    separators
    (throw (ex-info "Unknown language"
                    {:chunk/error :unknown-language
                     :language lang
                     :known (set (keys language-separators))}))))

(defn- split-on
  "Split s on literal separator sep, optionally attaching it to an adjacent piece."
  [^String s ^String sep keep-separator]
  (if (or (= sep "") (= keep-separator false))
    (if (= sep "")
      (mapv str s)
      (->> (str/split s (re-pattern (Pattern/quote sep)) -1)
           (filterv (complement #(= "" %)))))
    (let [separator-length (count sep)]
      (loop [piece-start 0, search-start 0, pieces []]
        (let [at (.indexOf s sep search-start)]
          (if (neg? at)
            (cond-> pieces
              (< piece-start (count s)) (conj (subs s piece-start)))
            (if (= keep-separator :start)
              (recur at (+ at separator-length) (cond-> pieces
                                                  (< piece-start at)
                                                  (conj (subs s piece-start at))))
              (recur (+ at separator-length) (+ at separator-length)
                     (conj pieces (subs s piece-start (+ at separator-length)))))))))))

(defn- join-trim [pieces sep]
  (let [d (str/join sep pieces)]
    (when-not (str/blank? d) (str/trim d))))

(defn- joined-length [pieces sep length-fn cache]
  (let [joined (str/join sep pieces)]
    (if (contains? @cache joined)
      (get @cache joined)
      (let [length (long (length-fn joined))]
        (swap! cache assoc joined length)
        length))))

(defn- trim-overlap
  "Pop pieces off the front of the current buffer until it is within the overlap
  budget (and small enough to admit the next piece)."
  [cur next-piece sep chunk-size overlap length-fn cache]
  (loop [cur cur, cur-len (joined-length cur sep length-fn cache)]
    (if (and (seq cur)
             (or (> cur-len (long overlap))
                 (> (joined-length (conj cur next-piece) sep length-fn cache)
                    (long chunk-size))))
      (let [cur (subvec cur 1)]
        (recur cur (joined-length cur sep length-fn cache)))
      cur)))

(defn- merge-splits
  "Greedily pack pieces (each already <= chunk-size) into chunks of <= chunk-size,
  joined by sep, carrying `overlap` worth of trailing pieces into the next chunk."
  [pieces sep chunk-size overlap length-fn cache]
  (loop [pieces (seq pieces), cur [], out []]
    (if-let [d (first pieces)]
      (let [candidate (conj cur d)
            candidate-len (joined-length candidate sep length-fn cache)]
        (if (and (seq cur) (> candidate-len (long chunk-size)))
          (let [doc (join-trim cur sep)
                out (cond-> out doc (conj doc))
                cur (trim-overlap cur d sep chunk-size overlap length-fn cache)]
            (recur pieces cur out))                      ; retry same d on trimmed buffer
          (recur (next pieces) candidate out)))
      (if-let [doc (join-trim cur sep)]
        (conj out doc)
        out))))

(defn- recursive-split [text separators chunk-size overlap length-fn keep-separator cache]
  (let [sep (or (some #(when (and (not= "" %) (str/includes? text %)) %) separators)
                (last separators))
        deeper-seps (vec (rest (drop-while #(not= % sep) separators)))
        pieces (split-on text sep keep-separator)]
    (loop [pieces (seq pieces), good [], out []]
      (if-let [p (first pieces)]
        (if (<= (long (if (contains? @cache p)
                         (get @cache p)
                         (let [length (long (length-fn p))]
                           (swap! cache assoc p length)
                           length)))
                    chunk-size)
          (recur (next pieces) (conj good p) out)
          (let [join-sep (if (= keep-separator false) sep "")
                merged (if (seq good)
                         (merge-splits good join-sep chunk-size overlap
                                       length-fn cache)
                         [])
                deeper (if (seq deeper-seps)
                         (recursive-split p deeper-seps chunk-size overlap
                                          length-fn keep-separator cache)
                         [p])]                            ; nothing finer to try -> keep whole
            (recur (next pieces) [] (into (into out merged) deeper))))
        (into out (when (seq good)
                    (merge-splits good (if (= keep-separator false) sep "")
                                  chunk-size overlap length-fn cache)))))))

(defn split
  "Split `text` into a vector of chunk strings.

  Options:
  - `:chunk-size` max size of a chunk, in `:length-fn` units (default 1000)
  - `:overlap`    size of trailing context repeated at the start of the next chunk (default 0)
  - `:separators` ordered split boundaries, coarsest first (default `default-separators`)
  - `:language`   keyword selecting `language-separators`; conflicts with `:separators`
  - `:keep-separator` `:start` (default), `:end`, or `false`; attach separators to the
                      following or preceding piece, or drop them
  - `:length-fn`  measures a string's size; default `count` (characters). Pass a token
                  counter to chunk by tokens.

  A piece with no admissible finer separator (an \"atom\" longer than `:chunk-size`) is
  emitted whole rather than dropped."
  ([text] (split text nil))
  ([text opts]
   (let [opts (or opts {})
         {:keys [chunk-size overlap separators language length-fn keep-separator]
          :or {chunk-size 1000 overlap 0 length-fn count}} opts
         separators (cond
                      (and (contains? opts :language) (contains? opts :separators))
                      (throw (ex-info "Conflicting options"
                                      {:chunk/error :conflicting-options
                                       :options #{:language :separators}}))

                      (contains? opts :language) (separators-for language)
                      (contains? opts :separators) separators
                      :else default-separators)]
     (if (str/blank? (str text))
       []
       (recursive-split text (vec separators) chunk-size overlap length-fn
                        (if (contains? opts :keep-separator) keep-separator :start)
                        (atom {}))))))

(defn split-with-offsets
  "Split `text` into maps with `:text`, `:start`, and `:end` offsets.

  Options match `split`. Offsets are character indices into the original text. With
  `:keep-separator false`, a chunk that cannot be located as an exact substring has
  nil offsets."
  ([text] (split-with-offsets text nil))
  ([text opts]
   (let [source (str text)]
     (loop [chunks (seq (split text opts)), lower-bound 0, out []]
       (if-let [chunk (first chunks)]
         (let [start (.indexOf ^String source ^String chunk (int lower-bound))
               found? (not (neg? start))
               end (when found? (+ start (count chunk)))
               next-lower-bound (if found? (inc start) lower-bound)]
           (recur (next chunks) next-lower-bound
                  (conj out {:text chunk :start (when found? start) :end end})))
         out)))))
