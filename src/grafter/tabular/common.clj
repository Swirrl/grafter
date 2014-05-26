(ns grafter.tabular.common
  (:use [clojure.java.io :only [file]])
  (:require [incanter.core :as ic])
  (:require [me.raynes.fs :as fs])
  (:require [clj-excel.core :as xls])
  (:import [java.io File]
           [org.apache.poi.xssf.usermodel XSSFWorkbook XSSFSheet]
           [org.apache.poi.hssf.usermodel HSSFWorkbook HSSFSheet]
           [org.apache.poi.ss.usermodel Workbook Sheet]
           [incanter.core Dataset]))

(defn- extension [f]
  (when-let [ext (-> f fs/extension)]
    (-> ext
        (.substring 1)
        keyword)))

(defmulti open-tabular-file (fn [f & {:keys [ext]}]
                              (or ext (extension f))))

(def datasetable? #{:csv})

(def dataset-holder-extensions
  "File types that are virtual folders which contain datasets."
  #{:csv :xls :xlsx})

(defn multiple-dataset-holder? [f]
  (-> (extension f)
      #{:xls :xlsx :ods}))

(defn dataset-holder? [f]
  (-> (extension f)
      dataset-holder-extensions))

(defn dataset-files
  "Given a directory, return a seq of files that can contain
  datasets."
  [dir]
  (->> (file-seq (fs/file dir))
       (filter dataset-holder?)))

(defn without-metadata-columns [[context sheet]]
  sheet)

(defn with-metadata-columns [[context sheet :as pair]]
  ;; TODO add columns to sheet here
  pair)

(defn- pair-with-context [file sheet]
  (let [common-context {:path (.getParent file)
                        :file (.getName file)}]

    (if (instance? Sheet sheet)
      [(assoc common-context :sheet-name (.getSheetName sheet))
       (xls/lazy-sheet sheet)]

      [common-context sheet])))


(defn open-all-sheets
  "Return a seq of sheets, recursively found beneath a given
  directory.

  By default it returns the sheets un-altered by using
  without-metadata-columns as its metadata function.

  You can provide it with other metadata functions which will splice
  the context into the sheet as new columms.
"
  ([dir] (open-all-sheets dir without-metadata-columns))
  ([dir add-metadata-f]
     (let [file->sheets (fn [dataset-file]
                          (let [dataset (->> dataset-file
                                             open-tabular-file)

                                sheets  (if (multiple-dataset-holder? dataset-file)
                                          (->> dataset xls/sheets)
                                          [dataset])

                                combine-metadata-f (comp add-metadata-f
                                                         (partial pair-with-context dataset-file))]

                            (map combine-metadata-f sheets)))]
       (mapcat file->sheets
               (dataset-files dir))

       )))


(comment

  (nth  (open-all-sheets (fs/file "./examples/data")) 4)

  )
