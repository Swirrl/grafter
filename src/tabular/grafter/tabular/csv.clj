(ns grafter.tabular.csv
  {:no-doc true}
  (:require [clojure-csv.core :as csv]
            [clojure.java.io :as io]
            [grafter.tabular.common :as tab])
  (:import [java.io IOException]))

(defmethod tab/read-dataset* :csv
  [f opts]
  (let [csv-seq (tab/mapply csv/parse-csv (io/reader f) opts)]
    (if (nil? csv-seq)
      (throw (IOException. (str "There was an error loading the CSV file: " f)))
      (tab/make-dataset csv-seq))))

(defmethod tab/read-datasets* :csv
  [f opts]
  (when-let [ds (tab/read-dataset* f opts)]
    [{"csv" ds}]))
