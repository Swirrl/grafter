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

  (let [headers (:column-names dataset)]
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
  (let [rows (indexed (:rows dataset))
        filtered-rows (select-indexed rows row-numbers)]
    (make-dataset (:column-names dataset)
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
  (let [column-names (:column-names dataset)
        matched-columns (->> cols
                             (map (partial col-position column-names)))
        selected-cols (select-indexed (indexed column-names) matched-columns)]
    (all-columns dataset selected-cols)))

(defn- select-columns-from-row
  {:deprecated true}
  [cols row]
  ;; Makes use of the fact that rows (vectors) are functions
  ;; of their indices.
  (apply vector (map (apply vector row) cols)))

(defn drop-rows [csv n]
  "Drops the first n rows from the CSV."
  (drop n csv))

(defn take-rows [csv n]
  "Drops the first n rows from the CSV."
  (take n csv))

(defmulti grep
  "Filters rows in the table for matches.  This is multi-method
  dispatches on the type of its second argument.  It also takes any
  number of column numbers as the final set of arguments.  These
  narrow the scope of the grep to only those columns.  If no columns
  are specified then grep operates on all columns."
  (fn [table f & cols] (class f)))

(defmethod grep clojure.lang.IFn [csv f & cols]
  (let [select-cols (if (empty? cols)
                      identity
                      (partial select-columns-from-row cols))]
    (filter (fn [row]
              (some f (select-cols row))) csv)))

(defmethod grep java.lang.String [csv s & cols]
  (apply grep csv #(.contains % s) cols))

(defmethod grep java.util.regex.Pattern [csv p & cols]
  (apply grep csv #(re-find p %) cols))

(defn- remove-indices [col & idxs]
  "Removes the values at the supplied indexes from the given vector."
  (let [pos (map - (sort idxs) (iterate inc 0))
        remove-index (fn [col pos]
                       (vec (concat (subvec col 0 pos)
                                    (subvec col (inc pos)))))]
    (reduce remove-index col pos)))

(defn- fuse-row [columns f row]
  (let [to-drop (drop 1 (sort columns))
        merged (assoc row (apply min columns)
                      (apply f (select-columns-from-row columns row)))]
    (apply remove-indices merged to-drop)))

(defn fuse [csv f & cols]
  "Merge columns with the specified function f receives the number of
cols supplied number of arguments e.g. If you fuse 3 columns f
must accept 3 arguments"
  (map (partial fuse-row cols f) csv))

(defn mapr [csv f]
  "Logically identical to map but with reversed arguments, so it works
better with -> .  mapr maps f over each row."
  (map f csv))

(defn mapc [csv fs]
  "Takes an array of functions and maps each to the equivalent column
position for every row."
  (->> csv
       (map (fn [row]
              (map (fn [f c] (f c))
                   (lazy-cat fs (cycle [identity])) row)))
       (map (partial apply vector))))

(defn swap [csv col-map]
  "Takes a map from column_id -> column_id (int -> int) and swaps each
column."

  (map (fn [row]
         (reduce (fn swaper [acc [cola colb]]
                   (-> row
                       (assoc cola (row colb))
                       (assoc colb (row cola))))
                 [] col-map))
       csv))

(defn derive-column
  "Adds a new column to the end of the row which is derived from
column with position col-n.  f should just return the cells value.

If no f is supplied the identity function is used, which results in
the specified column being cloned."

  ;; todo support multiple columns/arguments to f.
  ([csv col-n]
     (derive-column csv identity col-n))

  ([csv f col-n]
     (map #(conj % (f (nth % col-n))) csv)))

;; alias to create a lightweight pattern matching style syntax for use
;; with mapc
(def _ identity)

(defn select-columns
  ([srange row]
     (drop srange row))
  ([srange erange row]
     {:pre [(<= srange erange)]}
     (let [ncols (- (inc erange) srange)]
       (->> row
            (drop srange)
            (take ncols)))))


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

(comment
  ;; TODO implement inner join, maybe l/r outer joins too
  (defn join [csv f & others]
    ;;(filter)
    (apply map vector csv others)))
