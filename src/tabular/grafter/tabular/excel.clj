(ns grafter.tabular.excel
  {:no-doc true}
  (:require [clj-excel.core :as xls]
            [grafter.tabular.common :as tab]
            [clojure.java.io :as io])
  (:import
   [java.net URI URL]
   [org.apache.poi.ss.usermodel Cell]))

;; Extend the clj-excel multi-method to handle expected grafter types
;; when outputting as an Excel file.
(defmethod xls/cell-mutator org.openrdf.model.URI [^Cell cell ^org.openrdf.model.URI val] (.setCellValue cell (str val)))
(defmethod xls/cell-mutator URI [^Cell cell ^URI val] (.setCellValue cell (str val)))
(defmethod xls/cell-mutator URL [^Cell cell ^URI val] (.setCellValue cell (str val)))
(defmethod xls/cell-mutator :default [^Cell cell val] (.setCellValue cell (str val)))

(defn- sheets
  "Returns a seq of maps from sheet-name => sheet-data in the order
  they are in the workbook."
  [wb & [fname]]
  (map (fn [name sheet]
         {name (let [ds (tab/make-dataset (xls/lazy-sheet sheet))]
                 (if fname
                   (tab/assoc-data-source-meta ds fname)
                   ds))})
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
  [source opts]
  (-> source
      xls/workbook-hssf
      (read-dataset** opts)))

(defmethod tab/read-dataset* "application/vnd.ms-excel"
  [source opts]
  (tab/read-dataset* source (merge opts {:format :xls})))

(defmethod tab/read-dataset* "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
  [source opts]
  (tab/read-dataset* source (merge opts {:format :xlsx})))

(defmethod tab/read-dataset* :xlsx
  [source opts]
  (-> source
      xls/workbook-xssf
      (read-dataset** opts)))

(defmethod tab/read-datasets* :xls
  [source opts]
  (-> source
      xls/workbook-hssf
      (sheets source)))

(defmethod tab/read-datasets* :xlsx
  [source opts]
  (-> source
      xls/workbook-xssf
      (sheets source)))

(defmethod tab/read-datasets* "application/vnd.ms-excel"
  [source opts]
  (tab/read-datasets* source (merge opts {:format :xls})))

(defmethod tab/read-datasets* "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
  [source opts]
  (tab/read-datasets* source (merge opts {:format :xlsx})))

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

(defmethod tab/write-dataset* "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
  [destination dataset opts]
  (tab/write-dataset* destination dataset (merge opts {:format :xlsx})))

(defmethod tab/write-dataset* "application/vnd.ms-excel"
  [destination dataset opts]
  (tab/write-dataset* destination dataset (merge opts {:format :xls})))

(tab/register-format-alias tab/read-dataset* :xls "application/vnd.ms-excel")
(tab/register-format-alias tab/read-dataset* :xlsx "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")

(tab/register-format-alias tab/read-datasets* :xls "application/vnd.ms-excel")
(tab/register-format-alias tab/read-datasets* :xlsx "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")

(tab/register-format-alias tab/write-dataset* :xls "application/vnd.ms-excel")
(tab/register-format-alias tab/write-dataset* :xlsx "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
