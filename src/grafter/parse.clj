(ns grafter.parse
  (:use [clojure.algo.monads])
  (:require [clj-time.core :as t]
            [clojure.string :as s]))

(defmonad blank-m
  "Like the maybe monad maybe-m, but it treats both nil and the blank
  string as Nothing (m-zero).  nils are lifted to blank strings"

  [m-zero ""

   m-result (fn m-result-blank [v] v)

   m-bind   (fn m-bind-blank [mv f]
              (if-not (or (nil? mv) (= m-zero mv))
                (if (string? mv)
                  (f mv)
                  (try
                    ;; if we're not a string try calling f on it anyway... if it errors
                    ;; return the value and move on.
                    ;;
                    ;; Effectively this means that nils flow backwards
                    ;; out of the pipeline, and strings and types flow up.
                    ;;
                    ;; This is probably a bad idea as it means errors
                    ;; are handled differently for each case (string
                    ;; or other-class) but it works for now! :-\
                    (f mv)
                    (catch Exception e
                      mv)))
                m-zero))

   m-plus   (fn m-plus-blank [& mvs]
              (first (drop-while (partial = m-zero))))])

(defmacro lift-1 [f]
  `(m-lift 1 ~f))

(def parse-int #(Integer/parseInt %))

(def trim s/trim)

(defn replacer [match replace]
  (fn [s]
    (s/replace s match replace)))

(def date-time t/date-time)
