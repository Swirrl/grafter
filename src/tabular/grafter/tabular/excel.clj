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

(defn- open-dataset** [wb {:keys [sheet] :as opts}]
  (-> wb
      sheets
      (get-sheet-map sheet)
      (get-sheet sheet)))

(defmethod tab/open-dataset* :xls
  [filename opts]
  (-> filename
      xls/workbook-hssf
      (open-dataset** opts)))

(defmethod tab/open-dataset* :xlsx
  [filename opts]
  (-> filename
      xls/workbook-xssf
      (open-dataset** opts)))

(defn- open-datasets** [wb opts]
  (->> wb
       sheets))

(defmethod tab/open-datasets* :xls
  [filename opts]
  (-> filename
      xls/workbook-hssf
      (open-datasets** opts)))

(defmethod tab/open-datasets* :xlsx
  [filename opts]
  (-> filename
      xls/workbook-xssf
      (open-datasets** opts)))
