(ns grafter.tabular.csv
  {:no-doc true}
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [grafter.tabular.common :as tab]
            [grafter.rdf.protocols :refer [raw-value]])
  (:import [java.io IOException]
           [org.apache.commons.io.input BOMInputStream]))

(defmethod tab/read-dataset* :csv
  [source opts]
  (let [reader (-> source
                   io/input-stream
                   BOMInputStream.
                   (#(tab/mapply io/reader % opts)))
        csv-seq (tab/mapply csv/read-csv reader opts)]
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
          stringified-rows (map (partial map raw-value) rows)]
      (apply csv/write-csv output
             stringified-rows
             (mapcat identity opts)))))

(tab/register-format-alias tab/read-dataset* :csv "text/csv")
(tab/register-format-alias tab/read-datasets* :csv "text/csv")
(tab/register-format-alias tab/write-dataset* :csv "text/csv")
