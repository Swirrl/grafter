(ns grafter.tabular
  (:require [clojure.java.io :as io]
            [grafter.tabular.common :as tabcommon]
            [grafter.sequences :as seqs]
            [grafter.tabular.excel]
            [clojure.string :as str]
            [grafter.tabular.csv]
            [incanter.core :as inc]
            [potemkin.namespaces :refer [import-vars]])
  (:import [incanter.core Dataset]))

(import-vars
 [grafter.tabular.common
  make-dataset
  copy-first-row-to-header
  move-first-row-to-header
  open-tabular-file
  open-all-datasets
  with-metadata-columns
  without-metadata-columns])



(defn test-dataset [r c]
  "Constructs a test dataset of r rows by c cols e.g.

(test-dataset 2 2) ;; =>

| A | B |
|---+---|
| 0 | 0 |
| 1 | 1 |"

  (->> (iterate inc 0)
       (map #(repeat c %))
       (take r)
       make-dataset))

(def column-names
  "If given a dataset, it returns its column names. If given a dataset and a sequence
  of column names, it returns a dataset with the given column names."
  inc/col-names)

(defn- resolve-col-id [column-key headers not-found]
  (let [converted-column-key (cond
                              (string? column-key) (keyword column-key)
                              (keyword? column-key) (name column-key)
                              (integer? column-key) (nth headers column-key not-found))]
    (if-let [val (some #{column-key converted-column-key} headers)]
      val
      not-found)))

(defn resolve-column-id
  "Finds and resolves the column id by converting between symbols and
  strings.  If column-key is not found in the datsets headers then nil
  is returned."
  [dataset column-key not-found]

  (let [headers (column-names dataset)]
    (resolve-col-id column-key headers not-found)))

(defn invalid-column-keys
  "Takes a sequence of column key names and a dataset and returns a
  sequence of keys that are not in the dataset."
  [keys dataset]

  (let [not-found (Object.)
        not-found-items (->> keys
                             (map (fn [col]
                                    [col (resolve-column-id dataset col not-found)]))
                             (filter (fn [[_ present]] (= not-found present)))
                             (map first))]
    not-found-items))

(defn all-columns [dataset cols]
  "Takes a dataset and any number of integers corresponding to column
  numbers and returns a dataset containing only those columns.

  If you want to use infinite sequences of columns or allow the
  specification of more cols than are in the data without error you
  should use columns instead.  Using an infinite sequence with this
  function will result in non-termination.

  One advantage of this over using columns is that you can duplicate
  an arbitrary number of columns."

  (let [not-found-items (invalid-column-keys cols dataset)]
    (if (and (empty? not-found-items)
            (some identity cols))
      (inc/$ cols dataset)
      (throw (IndexOutOfBoundsException. (str "The columns: " (str/join ", " not-found-items) " are not currently defined."))))))

(defn- indexed [col]
  (map-indexed vector col))

(defn- rows-bounded [row-data row-numbers]
  (let [row-numbers (into #{} row-numbers)]
    (->> row-data
         (filter (fn [[index row]]
                   (if (row-numbers index)
                     true
                     false)))
         (map second))))

(defn- select-indexed
  "Selects indexed rows or columns (outside of the dataset).  Assumes the seq of
  row-numbers to select on is ordered, and that row-data is a tuple
  of form [index row].

Returns a lazy sequence of matched rows."
  [[[index current-item] & item-data]
   [current-item-number & rest-item-numbers :as item-numbers]]
  (cond
   (or (nil? current-item-number)
       (nil? index)
       (= ::not-found current-item-number)) []

       (= current-item-number index) (let [[repeated-item-numbers remaining-item-numbers]
                                      (split-with #(= current-item-number %) item-numbers)
                                      repeated-items (repeat (count repeated-item-numbers) current-item)]
                                  (lazy-cat
                                   repeated-items
                                   (select-indexed item-data remaining-item-numbers)))

       (< current-item-number index) (select-indexed
                                     (drop-while (fn [[index item]]
                                                   (not= index current-item-number))
                                                 item-data)
                                     rest-item-numbers)
       (> current-item-number index) (select-indexed
                                     (drop-while (fn [[index item]]
                                                   (not= index current-item-number))
                                                 item-data)
                                     ;; leave item-numbers as is (i.e. stay on current item after fast forwarding the data)
                                     item-numbers)))

(defn rows [dataset row-numbers & {:as opts}]
  (let [rows (indexed (inc/to-list dataset))
        filtered-rows (select-indexed rows row-numbers)]
    (make-dataset (column-names dataset)
                  filtered-rows)))

(defn- col-position [column-names col]
  (if-let [canonical-col (resolve-col-id col column-names ::not-found)]
    (let [val (.indexOf column-names canonical-col)]
      (if (not= -1 val)
        val
        ::not-found))))

(defn columns
  "Given a dataset and some columns, narrow the dataset to just the
  supplied columns.

  cols are paired off with columns in the data and then a selection is
  done.  Any cols left over after the pairing are discarded, but if a
  selected col is not actually in the data an IndexOutOfBoundsException will
  be thrown.

  This function can safely be used with infinite sequences."

  [dataset cols]
  (let [col-names (column-names dataset)
        matched-columns (->> cols
                             (map (partial col-position col-names)))
        selected-cols (select-indexed (indexed col-names) matched-columns)]
    (all-columns dataset selected-cols)))

(defn map-rows
  "Maps the function f over the dataset and returns a new dataset.
  This is really just map/fmap defined on dataset data."
  [dataset f]
  (make-dataset (column-names dataset)
                (->> dataset inc/to-list f)))

(defn- map-keys [f hash]
  "Apply f to the keys in the supplied hashmap and return a new
  hashmap."
  (zipmap (map f (keys hash))
          (vals hash)))

(defn rename-columns
  "Renames the columns"
  [dataset col-map-or-fn]
  {:pre [(or (map? col-map-or-fn)
             (ifn? col-map-or-fn))]}

  (if (map? col-map-or-fn)
    (inc/rename-cols col-map-or-fn dataset)
    (let [old-key->new-key (partial map-keys col-map-or-fn)
          new-data (map (fn [row]
                          (map-keys col-map-or-fn row))
                        (inc/to-list dataset))
          new-columns (map col-map-or-fn
                           (column-names dataset))]
      (make-dataset new-columns
                    new-data))))

(defn drop-rows [dataset n]
  "Drops the first n rows from the CSV."
  (map-rows dataset (partial drop n)))

(defn take-rows [dataset n]
  "Drops the first n rows from the CSV."
  (map-rows dataset (partial take n)))

(defn derive-column
  "Adds a new column to the end of the row which is derived from
column with position col-n.  f should just return the cells value.

If no f is supplied the identity function is used, which results in
the specified column being cloned."

  ([dataset new-column-name from-cols]
   (derive-column dataset new-column-name from-cols identity))
  ;; todo support multiple columns/arguments to f.
  ([dataset new-column-name from-cols f]
     (inc/add-derived-column new-column-name from-cols f dataset)))

(defn- grep-row [dataset f]
  (let [filtered-data (filter f (:rows dataset))]
    (make-dataset (column-names dataset)
                  filtered-data)))

(defmulti grep
  "Filters rows in the table for matches.  This is multi-method
  dispatches on the type of its second argument.  It also takes any
  number of column numbers as the final set of arguments.  These
  narrow the scope of the grep to only those columns.  If no columns
  are specified then grep operates on all columns."
  (fn [table f & cols] (class f)))

(defn- cells-from-columns
  "Returns a seq of cells matching the supplied columns, cells are
  stripped of column names by this process.  If no columns are specified all the cell
  values for the row are returned."
  [col-set row]
  (->> row
       (filter (fn [[k v]] (col-set k)))
       (map second)))

(defmethod grep clojure.lang.IFn

  [dataset f & cols]
  (let [data (:rows dataset)
        cols (if (nil? cols)
                 (column-names dataset)
                 (first cols))
        col-set (into #{} cols)]

     (make-dataset (column-names dataset)
                   (->> data
                       (filter (fn [row]
                                 (some f
                                       (cells-from-columns col-set row))))))))

(defmethod grep java.lang.String [dataset s & cols]
  (apply grep dataset #(.contains % s) cols))

(defmethod grep java.util.regex.Pattern [dataset p & cols]
  (apply grep dataset #(re-find p %) cols))

(defn- remove-indices [col & idxs]
  "Removes the values at the supplied indexes from the given vector."
  (let [pos (map - (sort idxs) (iterate inc 0))
        remove-index (fn [col pos]
                       (vec (concat (subvec col 0 pos)
                                    (subvec col (inc pos)))))]
    (reduce remove-index col pos)))

(def _ identity)

(defn mapc [dataset fs]
"Takes an array or a hashmap of functions and maps each to the key column for every row."
  (let [fs-hash (if (vector? fs)
                    (zipmap (column-names dataset) fs)
                    fs)
        other-hash (zipmap (vec (clojure.set/difference (set (:column-names dataset))
                                                        (set (keys fs-hash))))
                           (cycle [identity]))
        functions (conj fs-hash other-hash)]
   (->> dataset
        (:rows)
        (map (fn [row]
               (apply merge (map #(hash-map % ((functions %) (row %)))
                    (keys row)))))
        (make-dataset (column-names dataset)))))


(defn swap
  "Takes an even numer of column names and swaps each column"

  ([dataset first-col second-col]
   (let [data (:rows dataset)
         header (column-names dataset)
         swapper (fn [v i j]
                   (-> v
                       (assoc i (v j))
                       (assoc j (v i))))]
     (-> header
         (swapper (col-position header first-col)
                  (col-position header second-col))
         (make-dataset data))))
  ([dataset first-col second-col & more]
   (if (even? (count more))
     (if (seq more)
       (reduce (fn [ds [f s]]
                 (swap ds f s))
               (swap dataset first-col second-col)
               (partition 2 more))
       (swap dataset first-col second-col))
     (throw (Exception. "Number of columns should be even")))))

(defn build-lookup-table
  "Takes a dataset, a vector of any number of column names corresponding
  to key columns and a column name corresponding to the value
  column.
  Returns a function, taking a vector of keys as
  argument and returning the value wanted"
  ([dataset key-cols]
   (let [key-names (map #(resolve-column-id dataset % "this column id doesn't exist!") key-cols)
         compl-key-cols (vec (clojure.set/difference (set (:column-names dataset))
                                                     (set (if (sequential? key-cols)
                                                            key-names
                                                            [key-names]))))]
     (build-lookup-table dataset key-cols compl-key-cols)))

  ([dataset key-cols value-col]

   (let [arg->vector (fn [x] (if (sequential? x) x [x]))
         keys (:rows (all-columns dataset (arg->vector key-cols)))
         val (:rows (all-columns dataset (arg->vector value-col)))
         table (zipmap keys val)]

     (fn [key-cells]
       (let [lookup (zipmap (arg->vector key-cols) (arg->vector key-cells))
             value-from-row (if (nil? (table lookup))
                              nil
                              ((table lookup) value-col))]
         value-from-row)))))


(comment

;; TODO fix this so that it doesn't assume contiguous blocks of
;; id/measure columns.  It needs to calculate the set complement of
;; the selected measure column ids and use those.

(defn normalise [[header-row & data-rows] measure-col-ids]
  "Takes a CSV with a header row and normalises it by transforming the
selected columns into values within the rows.

Essentially the following call:

(normalise csv 3 4)

will convert a table that looks like this:

| cola | colb | colc | normalise-me-a | normalise-me-b |
|------+------+------+----------------+----------------|
|    0 |    0 |    0 | normal-a-0     | normal-b-0     |
|    1 |    1 |    1 | normal-a-1     | normal-b-1     |

into data rows look like this.  It does not yet preserve the header row:

|   |   |   |                |            |
|---+---+---+----------------+------------+
| 0 | 0 | 0 | normalise-me-a | normal-0-a |
| 0 | 0 | 0 | normalise-me-b | normal-0-b |
| 1 | 1 | 1 | normalise-me-a | normal-1-a |
| 1 | 1 | 1 | normalise-me-b | normal-1-b |
"
  (let [ncols            (count header-row)
        colids           (-> (take ncols measure-col-ids) set sort) ;; incase its an infinite seq
        srange           (first colids)
        colids           (take (- ncols srange) colids)
        erange           (last colids)
        ncols            (count colids)
        headers-to-move  (select-columns srange erange header-row)

        normalise-row (fn [id row]
                        (let [rowv (->> row (take srange) (apply vector))]
                          (-> rowv
                              (conj (nth header-row id))
                              (conj (nth row id)))))

        expand-rows (fn [row] (map normalise-row colids (repeat ncols row)))]

    (mapcat expand-rows data-rows)))


  )



(comment
  ;; TODO implement inner join, maybe l/r outer joins too
  (defn join [csv f & others]
    ;;(filter)
    (apply map vector csv others)))

