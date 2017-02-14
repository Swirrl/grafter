(ns ^:no-doc grafter.tabular.transpose
  "Functions for transposing row and column data"
  (:require
   [grafter.tabular.common :refer :all]))

(defn- sorted-rows
  "Takes an incanter dataset and returns a seq of dataset rows where
  each row remains sorted as a sequence of tuples, according to column
  name order."
  [dataset]
  (map (fn [row]
         (->> (:column-names dataset)
              (map (fn [col-key]
                     (find row col-key)))))
       (:rows dataset)))

(defn transpose
  "Takes an incanter dataset and attempts to transpose rows with columns.
  That is to say, the table will be pivoted and the first vertical column
  will be used as column names.

  For example, given an input table:

  | :a | :b | :c | :d | :e | :f |
  |----+----+----+----+----+----|
  |  1 |  2 |  3 |  4 |  5 |  6 |
  |  9 | 10 | 11 | 12 | 13 | 14 |

  Transpose will transform to:

  | :a | 1 |  9 |
  |----+---+----|
  | :b | 2 | 10 |
  | :c | 3 | 11 |
  | :d | 4 | 12 |
  | :e | 5 | 13 |
  | :f | 6 | 14 |"
  [dataset]
  (let [transposed-rows (apply mapv
                               vector
                               (sorted-rows dataset))
        col-names (cons (first (:column-names dataset))
                        (map second
                             (first transposed-rows)))
        new-rows (map (fn [row]
                        (let [first-item-label (get-in row [0 0])
                              row-values (map second row)]
                          (cons first-item-label row-values)))
                      (rest transposed-rows))]
    (make-dataset new-rows col-names)))