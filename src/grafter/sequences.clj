(ns grafter.sequences
  "A library of useful lazy sequences.")

(defn integers-from
  ([n]
     (iterate inc n)))

(defn column-names-seq [alphabet]
  "Given an alphabet string generate a lazy sequences of column
  names e.g.

  (column-names-seq \"ABCDEFGHIJKLMNOPQRSTUVWXYZ\") ;; => (\"A\" \"B\" \"C\" ... \"AA\" \"AB\")
"
  (->> (map str alphabet)
       (iterate (fn [chars]
                  (for [x chars
                        y alphabet]
                    (str x y))))
       (apply concat)))

(defn alphabetical-column-names []
  (column-names-seq "ABCDEFGHIJKLMNOPQRSTUVWXYZ"))
