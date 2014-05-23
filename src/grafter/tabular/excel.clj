(ns grafter.tabular.excel
  (:require [grafter.tabular.util :as tabutil])
  (:require [clj-excel.core :as xls]))

(defmethod tabutil/open-as-table :xls
  [f & args]
  (-> f xls/workbook-hssf))

(defmethod tabutil/open-as-table :xlsx
  [f & args]
  (-> f xls/workbook-xssf))


(comment
  (for [workbook-file xls-files
        [sheet-name sheet-data] (-> workbook-file xls/workbook-xssf xls/lazy-workbook)
        :when ((complement ignore-sheets) sheet-name)]
    [workbook-file sheet-name sheet-data])

  )
