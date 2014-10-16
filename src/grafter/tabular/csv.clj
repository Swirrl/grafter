(ns grafter.tabular.csv
  {:no-doc true}
  (:require [clojure-csv.core :as csv]
            [clojure.java.io :as io]
            [grafter.tabular.common :as tab]))

(defmethod tab/open-tabular-file :csv
  [f & {:as opts}]
  (if opts
    (csv/parse-csv (io/reader f) opts)
    (csv/parse-csv (io/reader f))))

(comment

  ;; taken from Hampshire pivot-tool.  TODO consider rewriting in a
  ;; generic way.
  ;;
  ;; Do we want make-parents functionality built in?
  ;;
  ;; We probably want a serialize method that serialises anything to
  ;; the appropriate file type based on file extension/mime-type.

  (defn write-output-file! [file data]

    (io/make-parents file)

    (with-open [^java.io.BufferedWriter w (io/writer file)]
      (doseq [row data]
        (let [^String s (write-csv [row])]
          (.write w s))))))
