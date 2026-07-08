(ns chunk.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [chunk.core :as c]))

(deftest short-text-stays-whole
  (is (= ["hello world"] (c/split "hello world" {:chunk-size 100}))))

(deftest blank-yields-nothing
  (is (= [] (c/split "" {:chunk-size 100})))
  (is (= [] (c/split "   \n  " {:chunk-size 100})))
  (is (= [] (c/split nil {:chunk-size 100}))))

(deftest respects-chunk-size
  (let [text (str/join " " (repeat 100 "alpha"))         ; ~599 chars
        chunks (c/split text {:chunk-size 50 :overlap 0})]
    (is (> (count chunks) 1))
    (is (every? #(<= (count %) 50) chunks))
    (testing "no content lost (whitespace-insensitive)"
      (is (= (str/replace text #"\s" "")
             (str/replace (apply str chunks) #"\s" ""))))))

(deftest prefers-coarsest-boundary
  ;; each paragraph fits under the size, so it splits on \n\n and stops there
  (let [text "Alpha beta.\n\nGamma delta.\n\nEpsilon zeta."
        chunks (c/split text {:chunk-size 15 :overlap 0})]
    (is (= ["Alpha beta." "Gamma delta." "Epsilon zeta."] chunks))))

(deftest recurses-to-finer-separators
  ;; one long line, no paragraph breaks -> must drop to spaces
  (let [text (str/join " " (map #(str "w" %) (range 40)))
        chunks (c/split text {:chunk-size 25 :overlap 0})]
    (is (> (count chunks) 1))
    (is (every? #(<= (count %) 25) chunks))))

(deftest overlap-carries-tail-into-next
  (let [text (str/join " " (map str (range 30)))
        chunks (c/split text {:chunk-size 20 :overlap 10})]
    (is (> (count chunks) 1))
    (testing "adjacent chunks share boundary words"
      (is (every? (fn [[a b]]
                    (let [tail (set (take-last 3 (str/split a #"\s+")))
                          head (take 3 (str/split b #"\s+"))]
                      (some tail head)))
                  (partition 2 1 chunks))))))

(deftest pluggable-length-fn-counts-tokens-not-chars
  ;; measure size in whitespace tokens (stand-in for a real tokenizer)
  (let [wc (fn [s] (if (str/blank? s) 0 (count (str/split (str/trim s) #"\s+"))))
        text "one two three four five six seven eight nine ten"
        chunks (c/split text {:chunk-size 3 :overlap 0 :length-fn wc})]
    (is (every? #(<= (wc %) 3) chunks))
    (is (= "one two three" (first chunks)))))

(deftest oversized-atom-emitted-whole
  ;; no separator can break it below the size -> kept as-is rather than dropped
  (let [chunks (c/split "supercalifragilistic" {:chunk-size 5 :overlap 0
                                                :separators ["\n\n" "\n" " "]})]
    (is (= ["supercalifragilistic"] chunks))))

(deftest markdown-language-splits-on-heading-boundaries
  (let [text (str "Intro paragraph with enough text to stand alone.\n"
                  "## First section\n"
                  "Some markdown body text.\n"
                  "```clojure\n"
                  "(defn example [] :ok)\n"
                  "```\n"
                  "### Nested section\n"
                  "More markdown body text.\n"
                  "## Second section\n"
                  "Final markdown body text.")
        chunks (c/split text {:chunk-size 90 :overlap 0 :language :markdown})]
    (is (> (count chunks) 1))
    (is (some #(str/includes? % "First section") chunks))
    (is (some #(str/includes? % "Nested section") chunks))
    (is (some #(str/includes? % "Second section") chunks))
    (is (not-any? #(str/starts-with? % "## ") chunks))
    (is (not-any? #(str/starts-with? % "### ") chunks))))

(deftest python-language-splits-on-def-boundaries
  (let [text (str "def first():\n"
                  "    return 'alpha alpha alpha alpha alpha'\n"
                  "\n"
                  "def second():\n"
                  "    return 'beta beta beta beta beta'")
        chunks (c/split text {:chunk-size 70 :overlap 0 :language :python})]
    (is (= 2 (count chunks)))
    (is (str/includes? (first chunks) "def first():"))
    (is (str/includes? (second chunks) "second():"))))

(deftest clojure-language-splits-on-defn-boundaries
  (let [text (str "(defn first-fn []\n"
                  "  :alpha-alpha-alpha-alpha-alpha)\n"
                  "\n"
                  "(defn second-fn []\n"
                  "  :beta-beta-beta-beta-beta)")
        chunks (c/split text {:chunk-size 70 :overlap 0 :language :clojure})]
    (is (= 2 (count chunks)))
    (is (str/includes? (first chunks) "(defn first-fn"))
    (is (str/includes? (second chunks) "second-fn"))))

(deftest unknown-language-throws
  (try
    (c/separators-for :nope)
    (is false "Expected unknown language")
    (catch clojure.lang.ExceptionInfo e
      (is (= :unknown-language (:chunk/error (ex-data e))))
      (is (= :nope (:language (ex-data e))))
      (is (contains? (:known (ex-data e)) :python)))))

(deftest language-and-separators-conflict
  (try
    (c/split "hello world" {:chunk-size 5
                            :language :python
                            :separators [" "]})
    (is false "Expected conflicting options")
    (catch clojure.lang.ExceptionInfo e
      (is (= :conflicting-options (:chunk/error (ex-data e)))))))

(deftest language-separators-have-default-tail-and-literal-strings
  (doseq [[language separators] c/language-separators]
    (testing language
      (is (vector? separators))
      (is (= ["\n\n" "\n" " " ""] (subvec separators (- (count separators) 4))))
      (is (every? string? separators)))))

(deftest default-behavior-matches-explicit-default-separators
  (let [text "Alpha beta.\n\nGamma delta.\n\nEpsilon zeta."]
    (is (= (c/split text {:chunk-size 15 :overlap 0})
           (c/split text {:chunk-size 15 :overlap 0
                          :separators c/default-separators})))))
