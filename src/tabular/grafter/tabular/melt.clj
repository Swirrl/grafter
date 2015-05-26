(ns ^:no-doc grafter.tabular.melt
  "Functions for melting data and building variations on melt."
  (:require
   [clojure.set :as set]
   [grafter.tabular.common :refer :all]))

(defn mapcat-rows
  "Transforms a dataset by performing a mapcat operation on the
  rows. Each row in the input is transformed to multiple rows in the
  output by the given transform function.

  Accepts the arguments:

  - dataset: The dataset to transform.

  - columns: The collection of columns in the output dataset.

  - f: function (Row -> Seqable Row) which transforms each row in the
  input into a sequence of new rows in the output.

  Each output row should contain the columns passed in the columns
  parameter."

  [dataset columns f]
  (-> (make-dataset (mapcat f (:rows dataset)) columns)
      (with-meta (meta dataset))))

(defn- melt-gen
  "Generalised version of melt.

  Takes the following arguments:

  - dataset: The dataset to melt.

  - pivot-keys: The collection of fixed columns in the output table.

  - generated-column-names: The collection of new columns in the
  output table. The output table will have (concat pivot-keys
  generated-column-names) columns.

  - col-partition-fn: Function to group the non-fixed columns into a
  number of partitions. A new row will be created for every partition
  in every row in the input table. For example, if the input table has
  4 rows, and col-partition-fn creates 3 groups for the non-fixed
  columns in the input, then the output table will contain 3 * 4 = 12
  rows.

  - row-builder-fn: Function (ColumnPartition -> Row -> RowFragment).
  This function is used to create the variable fragment of an output
  row given the source row in the input table and the correspdoning
  column partition returned from col-partition-fn. The row in the
  output table is created by merging the variable fragment returned
  from this function with the fixed part defined by the
  pivot-keys. The returned fragment should contain values for the
  column names in the generated-column-names parameter."
  {:doc/format :markdown}

  [dataset pivot-keys generated-column-names col-partition-fn row-builder-fn]
  (let [canonicalise-key (partial resolve-column-id dataset)
        pivot-keys (map canonicalise-key (lift->vector pivot-keys))
        input-columns (map canonicalise-key (column-names dataset))
        output-columns (concat pivot-keys generated-column-names)
        melted-columns (set/difference (set input-columns) (set pivot-keys))
        ordered-melted-columns (keep melted-columns input-columns)
        col-partition (col-partition-fn ordered-melted-columns)
        f (fn [row]
            (let [pivot-values (select-keys row pivot-keys)]
              (map (fn [cp] (merge pivot-values (row-builder-fn cp row))) col-partition)))]
    (mapcat-rows dataset output-columns f)))

(defn melt
  "Melt an object into a form suitable for easy casting, like a melt function in R.
  It accepts multiple pivot keys (identifier variables that are
  reproduced for each row in the output).

  `(use '(incanter core charts datasets))`

  `(view (with-data (melt (get-dataset :flow-meter) :Subject)`

  `(line-chart :Subject :value :group-by :variable :legend true)))`

  See http://www.statmethods.net/management/reshape.html for more
  examples."
  {:doc/format :markdown}
  [dataset pivot-keys]
  (letfn [(col-partition [cols] (map (fn [c] [c]) cols))
          (build-row [[c] row] {:variable c :value (row c)})]
    (melt-gen dataset pivot-keys [:variable :value] col-partition build-row)))

(defn ^:no-doc melt-column-groups
  "Melts a dataset into groups defined by the list of given column
  names. Given a collection of pivot columns and a collection of group
  column names, this splits each row in the input into a collection of
  groups and creates a row in the output for each group. The groups
  are all the length of the column name group and it is an error if
  the size of the group does not divide the number of non-fixed
  columns exactly.

  For example, given an input table:

  | :measure | :q1-2013 | :q2-2013 | :q3-2013 | :q4-2013 | :q1-2014 | :q2-2014 | :q3-2014 | :q4-2014 |
  |--------------------------------------------------------------------------------------------------|
  | :sales   | 100      | 250      | 200      | 400      | 90       | 200      | 150      | 600      |

  This can be seen as a table with a fixed :measure column and two
  groups containing four financial quarters. This table can be
  converted with

  `(melt-column-groups [:sales] [:q1 :q2 :q3 :q4])`

  into the table:

  | :measure | :q1    | :q2   | :q3   | :q4   |
  |----------|--------|-------|-------|-------|
  | :sales   | 100    | 250   | 200   | 400   |
  | :sales   | 90     | 200   | 150   | 600   |

  Takes the arguments:

  - dataset: The input dataset to melt.

  - pivot-keys: The fixed group of columns to copy to each output row.

  - output-column-names: Collection of column names that defines the
  gropus from the input row."
  {:doc/format :markdown}
  [dataset pivot-keys output-column-names]
  (let [output-column-names (lift->vector output-column-names)]
    (letfn [(col-partition [cols]
              (let [group-size (count output-column-names)
                    col-count (count cols)]
                (if (= 0 (mod col-count group-size))
                  (partition group-size cols)
                  (throw (IllegalArgumentException.
                          (str "Column group size should be a multiple of the "
                               "number of non-fixed columns (" col-count ").")))))
              (partition (count output-column-names) cols))
            (build-row [cols row]
              (let [col-map (zipmap cols output-column-names)]
                (map-keys col-map (select-keys row cols))))]
      (melt-gen dataset pivot-keys output-column-names col-partition build-row))))
