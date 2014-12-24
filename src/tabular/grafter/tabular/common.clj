(ns grafter.tabular.common
  {:no-doc true}
  (:require [clj-excel.core :as xls]
            [grafter.sequences :as seqs]
            [clojure.java.io :as io]
            [incanter.core :as inc]
            [me.raynes.fs :as fs])
  (:import [org.apache.poi.ss.usermodel Sheet]
           [incanter.core Dataset]
           [java.io File InputStream]))


(defn mapply
  "Like apply, but f takes keyword arguments and the last argument is
  not a seq but a map with the arguments for f"
  [f & args]
  {:pre [(let [kwargs (last args)] (or (map? kwargs) (nil? kwargs)))]}
  (apply f (apply concat
                  (butlast args) (last args))))

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
  (when-let [^String ext (-> f fs/extension)]
    (-> ext
        (.substring 1)
        keyword)))

(defmulti open-dataset*
  "Opens a dataset from a datasetable thing i.e. a filename or an existing Dataset.
The multi-method dispatches based upon a :format option. If this isn't provided then
the type is used. If this isn't provided then we fallback to file extension.

Supported options are currently:

:ext - An overriding file extension (as keyword) to force a particular
       file type to be opened instead of looking at the files extension."

  (fn [file {:keys [format]}]
    (if (#{File String} (class file))
      (or format (extension file))
      (class file))))

(defmethod open-dataset* Dataset [dataset opts]
  dataset)

(defn- open-dataset-with-inferred-extension [dataset {:keys [format] :as opts}]
  (let [format (or format (extension dataset))]
    (open-dataset* (io/input-stream dataset) {:format (extension dataset)})))

(defmethod open-dataset* String [dataset opts]
  (open-dataset-with-inferred-extension dataset opts))

(defmethod open-dataset* File [dataset opts]
  (open-dataset-with-inferred-extension dataset opts))

(defmethod open-dataset* InputStream [dataset {:keys [format]}]
  (open-dataset* dataset {:format format}))

(defmethod open-dataset* ::default
  [dataset {:keys [format] :as opts}]
  (if (nil? format)
    (throw (IllegalArgumentException. (str "Please specify a format, it could not be infered when opening a dataset of type: " (class dataset))))
    (-> (io/input-stream dataset)
        (open-dataset* opts))))

(defn open-dataset
  "Opens a dataset from a datasetable thing i.e. a filename or an existing Dataset.
The multi-method dispatches based upon a :format option. If this isn't provided then
the type is used. If this isn't provided then we fallback to file extension.

Options are:

  :format - to force the datasetable to be opened with a particular method."
  [datasetable & {:keys [format] :as opts}]

  (open-dataset* datasetable opts))

(defmulti open-datasets*
  (fn [multidatasetable {:keys [format] :as opts}]
    (when (:sheet opts)
      (throw (IllegalArgumentException. "open-datasets cannot open a single sheet.  Use open-dataset* to do this.")))
    (or format
        (extension multidatasetable))))

(defn open-datasets
  "Opens a lazy sequence of datasets from a something that returns multiple
  datasetables - i.e. all the worksheets in an Excel workbook."

  [dataset & {:keys [format] :as opts}]
  (open-datasets* dataset opts))

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
