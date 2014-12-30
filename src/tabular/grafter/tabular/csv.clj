(ns grafter.tabular.csv
  {:no-doc true}
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [grafter.tabular.common :as tab])
  (:import [java.io IOException]))

(defmethod tab/read-dataset* :csv
  [f opts]
  (let [csv-seq (tab/mapply csv/read-csv (io/reader f) opts)]
    (if (nil? csv-seq)
      (throw (IOException. (str "There was an error loading the CSV file: " f)))
      (tab/make-dataset csv-seq))))

(defmethod tab/read-datasets* :csv
  [f opts]
  (when-let [ds (tab/read-dataset* f opts)]
    [{"csv" ds}]))

(defmethod tab/write-dataset* :csv [dataset destination {:keys [format] :as opts}]
  (with-open [output (io/writer destination)]
    (let [col-names (:column-names dataset)]
      (tab/mapply csv/write-csv output (tab/dataset->seq-of-seqs dataset) opts))))
