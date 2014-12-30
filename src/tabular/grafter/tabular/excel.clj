(ns grafter.tabular.excel
  {:no-doc true}
  (:require [clj-excel.core :as xls]
            [grafter.tabular.common :as tab]
            [clojure.java.io :as io]))

(defn- sheets
  "Returns a seq of maps from sheet-name => sheet-data in the order
  they are in the workbook."
  [wb]
  (map (fn [name sheet]
         {name (tab/make-dataset (xls/lazy-sheet sheet))})
       (xls/sheet-names wb) (xls/sheets wb)))

(defn- get-sheet-map [sheet-seq sheet]
  (if sheet
    (apply merge sheet-seq)
    (first sheet-seq)))

(defn- get-sheet [sheet-map sheet]
  (if sheet
    (get sheet-map sheet)
    (first (vals sheet-map))))

(defn- read-dataset** [wb {:keys [sheet] :as opts}]
  (-> wb
      sheets
      (get-sheet-map sheet)
      (get-sheet sheet)))

(defmethod tab/read-dataset* :xls
  [filename opts]
  (-> filename
      xls/workbook-hssf
      (read-dataset** opts)))

(defmethod tab/read-dataset* :xlsx
  [filename opts]
  (-> filename
      xls/workbook-xssf
      (read-dataset** opts)))

(defn- read-datasets** [wb opts]
  (->> wb
       sheets))

(defmethod tab/read-datasets* :xls
  [filename opts]
  (-> filename
      xls/workbook-hssf
      (read-datasets** opts)))

(defmethod tab/read-datasets* :xlsx
  [filename opts]
  (-> filename
      xls/workbook-xssf
      (read-datasets** opts)))

(defn write-dataset** [destination wb dataset-map]
  (with-open [output (io/writer destination)]
    (xls/save (xls/build-workbook wb dataset-map)
              destination)))

(defmethod tab/write-dataset* :xlsx
  [destination dataset {:keys [format sheet-name] :or {sheet-name "Sheet1" } :as opts}]
  (write-dataset** destination (xls/workbook-xssf)
                   {sheet-name (tab/dataset->seq-of-seqs dataset) }))

(defmethod tab/write-dataset* :xls
  [destination dataset {:keys [format sheet-name] :or {sheet-name "Sheet1" } :as opts}]

  (write-dataset** destination (xls/workbook-hssf)
                   {sheet-name (tab/dataset->seq-of-seqs dataset) }))
