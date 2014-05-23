(ns grafter.fs-dataset
  (:require [incanter.core :as ic])
  (:require [me.raynes.fs :as fs])
  (:require [clj-excel.core :as xls])
  (:import [java.io File]
           [org.apache.poi.xssf.usermodel XSSFWorkbook XSSFSheet]
           [org.apache.poi.hssf.usermodel HSSFWorkbook HSSFSheet]
           [org.apache.poi.ss.usermodel Workbook]))

;; experimental idea

;; Datasets are stored in arbitrary vfolders.  vfolders can be nested
;; to arbitrary depths and contain either things that can be made into
;; Dataset's, or they contain more vfolders or things we can't
;; identify as datasets (exlcuded's).

(def datasetable? #{:csv})

(def dataset-holder?
  "File types that are virtual folders which contain datasets."
  #{:xls :xlsx})

(defn- extension [f]
  (when-let [ext (-> f fs/extension)]
    (-> ext
        (.substring 1)
        keyword)))

(defprotocol IDatasetTree
  (dataset? [n])
  (vfolder? [n])
  (children [n]))

(extend-protocol IDatasetTree
  File
  (dataset? [f]
    (and (datasetable? (extension f))
         (not (vfolder? f))))

  (vfolder? [f]
    (or (.isDirectory f)
        ;; todo find a way not to hardcode this here
        (not= nil (-> f extension #{:xls :xlsx}))))

  (children [f]
    (if (.isDirectory f)
      (filter vfolder? (-> f .listFiles seq))
      (if (dataset-holder? (extension f))
        (children (cond
                   (= (extension f) :xls) (xls/workbook-hssf f)
                   (= (extension f) :xlsx) (xls/workbook-hssf f))))))

  HSSFWorkbook
  (dataset? [wb]
    false)

  (vfolder? [wb]
    true)

  (children [wb]
    (xls/sheets wb))

  XSSFWorkbook
  (dataset? [wb]
    false)

  (vfolder? [wb]
    true)

  (children [wb]
    (xls/sheets wb))

  HSSFSheet
  (dataset? [sh]
    true)

  (vfolder? [sh]
    false)

  (children [sh]
    nil)

  XSSFSheet
  (dataset? [sh]
    true)

  (vfolder? [sh]
    false)

  (children [sh]
    nil))

(defn- ds-seq [fileseq]
  (when (seq fileseq)
    (lazy-seq
     (let [[node & rest] fileseq]
       (if (dataset? node)
         (cons node (ds-seq rest))
         (let [siblings (mapcat children rest)]
           (ds-seq siblings)))))))

(defn datasets-seq
  ([file-glob]
     (ds-seq (fs/glob file-glob))))
