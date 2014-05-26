(ns grafter.tabular.common
  (:use [clojure.java.io :only [file]])
  (:require [incanter.core :as ic])
  (:require [me.raynes.fs :as fs])
  (:require [clj-excel.core :as xls])
  (:import [java.io File]
           [org.apache.poi.xssf.usermodel XSSFWorkbook XSSFSheet]
           [org.apache.poi.hssf.usermodel HSSFWorkbook HSSFSheet]
           [org.apache.poi.ss.usermodel Workbook Sheet]))


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
  (->> (file-seq dir)
       (filter dataset-holder?)))

(defn add-metadata-to-sheet [[context sheet]]
  ;; TODO add columns to sheet here
  )

(defn- pair-with-context [file sheet]
  (let [common-context {:path (.getParent file)
                        :file (.getName file)}]

    (if (instance? Sheet sheet)
      [(assoc common-context :sheet-name (.getSheetName sheet))
       (xls/lazy-sheet sheet)]

      [common-context sheet])))


(defn open-all-sheets
  "Return a seq of sheets, recursively found beneath a given
  directory."
  ([dir] (open-all-sheets dir identity))
  ([dir add-metadata-f]
     (mapcat (fn [dataset-file]
               (let [dataset (->> dataset-file
                                  open-tabular-file)

                     sheets (if (multiple-dataset-holder? dataset-file)
                              (->> dataset xls/sheets)
                              [dataset])


                     combine-metadata-f (comp add-metadata-f (partial pair-with-context dataset-file))]

                 (map combine-metadata-f sheets)))

             (dataset-files dir))))


(comment

  (nth  (open-all-sheets (fs/file "./examples/data")) 4)

  )
