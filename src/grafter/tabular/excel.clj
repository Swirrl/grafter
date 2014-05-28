(ns grafter.tabular.excel
  (:require [grafter.tabular.common :as tab])
  (:require [clj-excel.core :as xls]))

(defmethod tab/open-tabular-file :xls
  [f & args]
  (-> f xls/workbook-hssf))

(defmethod tab/open-tabular-file :xlsx
  [f & args]
  (-> f xls/workbook-xssf))

(comment
  (for [workbook-file xls-files
        [sheet-name sheet-data] (-> workbook-file xls/workbook-xssf xls/lazy-workbook)
        :when ((complement ignore-sheets) sheet-name)]
    [workbook-file sheet-name sheet-data])

  )
