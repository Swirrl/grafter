(ns grafter.csv
  (:require [clojure-csv.core :as csv])
  (:require [clojure.java.io :as io]))

;; Just experimenting with CSV

(defn parse-csv [csv-file-or-url]
  (csv/parse-csv (io/reader csv-file-or-url)))

(defn nnth
  "Same as nth but returns nil (or not-found) if supplied."
  ([col index] (nnth col index nil))
  ([col index not-found]
     (try
       (nth col index not-found)
       (catch java.lang.IndexOutOfBoundsException ex
         not-found))))

(defn- select-columns-from-row [cols row]
  ;; Makes use of the fact that rows (vectors) are functions
  ;; of their indices.
  (apply vector (map row cols)))

(defn columns [csv & cols]
  "Takes a parsed CSV file and any number of integers corresponding to
column numbers and returns a new CSV file containing only those
columns."
  (map (partial select-columns-from-row cols) csv))

(defn rows
  ([csv] csv)
  ([csv r]
     (if (sequential? r)
       (apply rows csv r)
       (let [val (nnth csv r ::not-found)]
         (if (= ::not-found val)
           nil
           (list val)))))
  ([csv r & rs]
     (reduce (fn [acc r]
               (conj acc (nnth csv r)))
             [] (conj rs r))))

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
  "Merge columns with the specified function"
  (map (partial fuse-row cols f) csv))

;; TODO implement inner join, maybe l/r outer joins too
(defn join [csv f & others]
  ;(filter)
  (apply map vector csv others))

(comment

  (def earners (parse-csv "./examples/high-earners-pay-2012.csv"))

  (columns earners 2 1) ; => (["Richard" "Alderman"] ["David" "Allen, Dr"] ...)

  (rows earners (range 5 10))

  (def lookup '(["1" "Cats"] ["2" "Dogs"]))

  (def pets '(["1" "Sylvester"]
              ["2" "Fido"]))


  )
