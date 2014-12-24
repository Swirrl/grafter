(ns grafter.tabular.csv
  {:no-doc true}
  (:require [clojure-csv.core :as csv]
            [clojure.java.io :as io]
            [grafter.tabular.common :as tab])
  (:import [java.io IOException]))

(defmethod tab/open-dataset* :csv
  [f opts]
  (let [csv-seq (tab/mapply csv/parse-csv (io/reader f) opts)]
    (if (nil? csv-seq)
      (throw (IOException. (str "There was an error loading the CSV file: " f)))
      (tab/make-dataset csv-seq))))

(defmethod tab/open-datasets* :csv
  [f opts]
  (when-let [ds (tab/open-dataset f opts)]
    [{"csv" ds}]))
