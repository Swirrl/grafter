(ns grafter.tabular.edn
  {:no-doc true}
  (:require [grafter.tabular.common :as tab]
            [clojure.edn :as edn]
            [incanter.core :refer [->Dataset]]
            [clojure.java.io :refer [reader writer]])
  (:import [java.io File IOException]
           [incanter.core Dataset]))

(defn- load-dataset [contents]
  (apply ->Dataset contents))

(defmethod tab/read-dataset* :edn
  [source opts]

  ;; TODO: make this read lazily
  (let [edn-value (edn/read-string {:readers {'incanter.core.Dataset load-dataset}}
                                   (slurp source))]
    (if (instance? Dataset edn-value)
      edn-value
      (throw (ex-info (str "Unexpected object found in edn file.  Expected a Dataset, and got a " edn-value) {:error :file-format-error})))))

;; TODO read-datasets*

(defmethod tab/write-dataset* :edn [destination dataset opts]
  (with-open [out (writer destination)]
    (print-dup dataset out)))

(tab/register-format-alias tab/read-dataset* :edn "application/edn")
;;(tab/register-format-alias tab/read-datasets* :edn "application/edn")
(tab/register-format-alias tab/write-dataset* :csv "application/edn")
