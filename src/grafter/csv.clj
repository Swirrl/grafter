(ns grafter.csv
  (:require [clojure-csv.core :as csv])
  (:require [clojure.java.io :as io]))

;; Just experimenting with CSV

(defn parse-csv [csv-file-or-url]
  (csv/parse-csv   (io/reader csv-file-or-url)))

(defn nnth
  "Same as nth but returns nil (or not-found) if"
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
       (list (nnth csv r))))
  ([csv r & rs]
      (reduce (fn [acc r]
                (conj acc (nnth csv r)))
              [] (conj rs r))))

;; TODO make this extensible
;; Also don't test for equality of strings but that it contains the string...
;; also make it work with regexes.
(defn grep [csv f]
  (if (instance? String f)
    (grep csv #(= f %))
    (filter (fn [row]
              (some f row)) csv)))

(defn remove-indices [col & idxs]
  "Removes the values at the supplied indexes from the given vector."
  (let [pos (map - (sort idxs) (iterate inc 0))
        remove-index (fn [col pos]
                       (vec (concat (subvec col 0 pos)
                                    (subvec col (inc pos)))))]
    (reduce remove-index col pos)))

(defn- fuse-row [columns f row]
  (let [
        to-drop (drop 1 (sort columns))
        merged (assoc row (apply min columns)
                      (apply f (select-columns-from-row columns row)))]
    
    (apply remove-indices merged to-drop)))

(defn fuse [csv f & cols]
  "Merge columns with the specified function"
  (map (partial fuse-row cols f) csv ))

(defn join [])

(comment

  (def earners (parse-csv "./examples/high-earners-pay-2012.csv"))

  (columns earners 2 1) ; => (["Richard" "Alderman"] ["David" "Allen, Dr"] ...)

  (rows earners (range 5 10))

  (-> (parse-csv "./examples/high-earners-pay-2012.csv")
      (grep "John")
      (fuse #(str %1 " " %2) 2 1)
      (columns 2 1)
      (rows 1 10))
  
  )
