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
