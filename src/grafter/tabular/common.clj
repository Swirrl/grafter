(ns grafter.tabular.common
  (:use [clojure.java.io :only [file]]))



(defn file-extension
  "Accepts a java.io.File or a String representing a file path and
returns the files file extension as a Clojure keyword.

Returns nil if no file extension is found.
"

  [f]
  {:pre [(or (string? f) (instance? java.io.File f))]}

  (let [fname (cond
               (string? f) f
               (instance? java.io.File f) (.getName f))]

    (->> fname
         (re-find #".*(?:\.(.*))")
         last
         keyword)))

(defmulti open-as-table (fn [f & {:keys [ext]}]
                          (or ext (file-extension f))))
