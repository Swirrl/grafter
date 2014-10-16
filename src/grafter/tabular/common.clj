(ns grafter.tabular.common
  {:no-doc true}
  (:require [clj-excel.core :as xls]
            [grafter.sequences :as seqs]
            [incanter.core :as inc]
            [me.raynes.fs :as fs])
  (:import (org.apache.poi.ss.usermodel Sheet)))

(defn move-first-row-to-header
  "For use with make-dataset.  Moves the first row of data into the
  header, removing it from the source data."
  [[first-row & other-rows]]

  [first-row other-rows])

(defn make-dataset
  "Like incanter's dataset function except it can take a lazy-sequence
  of column names which will get mapped to the source data.

  Works by inspecting the amount of columns in the first row, and
  taking that many column names from the sequence.

  Inspects the first row of data to determine the number of columns,
  and creates an incanter dataset with columns named alphabetically as
  by grafter.sequences/column-names-seq."

  ([]
   (inc/dataset []))

  ([data]
   (if (sequential? data)
     (make-dataset data (seqs/alphabetical-column-names))
     data))

  ([data columns-or-f]
     {:pre [(or (ifn? columns-or-f)
                (sequential? columns-or-f))]}
     (let [[column-names data] (if (sequential? columns-or-f)
                                 [(if (inc/dataset? data)
                                    (take (-> data :column-names count) columns-or-f)
                                    (take (-> data first count) columns-or-f))
                                   data]
                                 (columns-or-f (if (inc/dataset? data)
                                                 (inc/to-list data)
                                                 data)))
           data (if (sequential? data)
                  data
                  (inc/to-list data))]

       (inc/dataset column-names data))))

(def column-names
  "If given a dataset, it returns its column names. If given a dataset and a sequence
  of column names, it returns a dataset with the given column names."
  inc/col-names)

(defn pass-rows
  "Passes the function f the collection of raw rows from the dataset
   and returns a new dataset containing (f rows) as its data.

  f should expect a collection of row maps and return a collection of
  rows.

  This function is intended to be used by Grafter itself and Grafter
  library authors.  It's not recommended to by users of the DSL
  because users of this function need to be aware of Dataset
  implementation details."
  [dataset f]
  (make-dataset (->> dataset :rows f)
                (column-names dataset)))

(defn- extension [f]
  (when-let [ext (-> f fs/extension)]
    (-> ext
        (.substring 1)
        keyword)))

(defmulti open-tabular-file
  "Takes a File or String as an argument and coerces it based upon its
file extension into its concrete low-level representation, provided by
the file adapter.

This is intended to be used by adapter developers, and developers who
need access to low level details.  Specific to the adapter.  Normal
users should prefer open-all-datasets.

Supported files are currently csv or Excel's xls or xlsx files.

Additionally open-as-table takes an optional set of key/value
parameters which will be passed to the concrete function opening the
file.

Supported options are currently:

:ext - An overriding file extension (as keyword) to force a particular
       file type to be opened instead of looking at the files extension."

  (fn [file & {:keys [ext]}]
    (or ext (extension file))))

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

(defn without-metadata-columns
  "Ignores any possible metadata and leaves the dataset as is."
  [[context data]]
  data)

(defn with-metadata-columns
  "Takes a pair of [context, data] and returns a dataset.  Where the
  metadata context is merged into the dataset itself."
  [[context data]]
  (letfn [(merge-metadata-column [dataset-acc [k v]]
            (inc/add-column k
                            (repeat v)
                            dataset-acc))]
    (reduce merge-metadata-column data context)))

(defn- pair-with-context [make-dataset-f file sheet-or-data]
  "Takes a function make-dataset-f a file representing the file
containing the dataset and the raw sheet object, which should either
be an apache poi Sheet or a seq-of-seqs row representation.

make-dataset-f should be a a function that converts a raw dataset type
into an incanter dataset.

Returns a vector pair containing a metadata map and the incanter
dataset."

  ;; ensure metadata is in a consistent sorted order
  (let [common-context (sorted-map :path (-> file .getCanonicalFile .getParent)
                                   :file (.getName file))]

    (if (instance? Sheet sheet-or-data)
      [(assoc common-context :sheet-name (.getSheetName sheet-or-data))
       (make-dataset-f (xls/lazy-sheet sheet-or-data))]
      [common-context (make-dataset-f sheet-or-data)])))

(defn open-all-datasets
  "Returns a sequence of incanter.core.Dataset's, recursively found beneath
  a given directory.

  Files may contain one or more datasets.

  By default it returns the sheets un-altered by using
  without-metadata-columns as its metadata function.

  You can provide it with other metadata functions which will splice
  the context into the sheet as new columms."

  [dir & {:keys [metadata-fn make-dataset-fn] :or {metadata-fn without-metadata-columns
                                                   make-dataset-fn make-dataset}}]
  (let [file->sheets (fn [dataset-file]
                       (let [dataset (->> dataset-file
                                          open-tabular-file)

                             ;; as native sheet types, either POI sheets or the raw clojure data representation
                             raw-sheets   (if (multiple-dataset-holder? dataset-file)
                                            (->> dataset xls/sheets)
                                            [dataset])

                             combine-metadata-fn (comp metadata-fn
                                                      (partial pair-with-context make-dataset-fn dataset-file))]

                         (map combine-metadata-fn raw-sheets)))]
    (mapcat file->sheets
            (dataset-files dir))))


(comment

  (nth  (open-all-sheets (fs/file "./examples/data")) 4)

  )
