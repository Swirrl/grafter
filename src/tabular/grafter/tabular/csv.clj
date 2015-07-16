(ns grafter.tabular.csv
  {:no-doc true}
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [grafter.tabular.common :as tab])
  (:import [java.io File IOException]
           [java.net URI URL]))

(defmethod tab/read-dataset* :csv
  [source opts]
  (let [csv-seq (tab/mapply csv/read-csv (tab/mapply io/reader source opts) opts)]
    (if (nil? csv-seq)
      (throw (IOException. (str "There was an error loading the CSV file: " source)))
      (tab/make-dataset csv-seq))))

(defmethod tab/read-datasets* :csv
  [source opts]
  (when-let [ds (tab/mapply tab/read-dataset source opts)]
    [{"csv" ds}]))

(defmethod tab/write-dataset* :csv [destination dataset {:keys [format] :as opts}]
  (with-open [output (io/writer destination)]
    (let [rows (tab/dataset->seq-of-seqs dataset)
          stringified-rows (map (partial map str) rows)]
      (apply csv/write-csv output
             stringified-rows
             (mapcat identity opts)))))

(tab/register-format-alias tab/read-dataset* :csv "text/csv")
(tab/register-format-alias tab/read-datasets* :csv "text/csv")
(tab/register-format-alias tab/write-dataset* :csv "text/csv")
