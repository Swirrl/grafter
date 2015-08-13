(ns grafter.sequences
  "A library of useful lazy sequences."
  (:require [clojure.string :refer [blank?]]))

(defn integers-from
  "Returns an infinite sequence of integers counting from the supplied
  starting number.

  Supports the use of an optional increment amount to allow increments
  of arbitrary or negative amounts.

  If no arguments are supplied an infinite sequence of all positive
  integers from 1 is returned."
  ([]
     (iterate inc 1))
  ([from]
     (iterate inc from))
  ([from inc-by]
     (iterate #(+ % inc-by) from)))

(defn column-names-seq
  "Given an alphabet string generate a lazy sequences of column names
  e.g.

  `(column-names-seq \"abcdefghijklmnopqrstuvwxyz\") ;; => (\"a\" \"b\" \"c\" ... \"aa\" \"ab\")`"
  [alphabet]
  (->> (map str alphabet)
       (iterate (fn [chars]
                  (for [x chars
                        y alphabet]
                    (str x y))))
       (apply concat)))

(defn alphabetical-column-names
  "Returns an infinite sequence of alphabetized column names.  If more
  than 26 are required the sequence will count AA AB AC ... BA BB BC
  ... ZZZA ... etc"
  []
  (column-names-seq "abcdefghijklmnopqrstuvwxyz"))

(defn fill-when
  "Takes a sequence of values and copies a value through the sequence
  depending on the supplied predicate function.

  The default predicate function is not-blank? which means that a cell
  will be copied through the sequence over blank cells until the next
  non-blank one.  For example:

  `(fill-when [:a \"\" \" \" :b nil nil nil]) ; => (:a :a :a :b :b :b :b)`

  A start value to copy can also be provided as the 3rd argument."
  ([col] (fill-when (complement blank?) col))
  ([p col] (fill-when p col (first col)))
  ([p col start]
     (when (seq col)
       (let [f (first col)
             current (if (p f) f start)]
         (cons current (lazy-seq (fill-when p (next col) current)))))))
