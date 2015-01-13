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

(defn dataset?
  "Predicate function to test whether the supplied argument is a
  dataset or not."
  [ds]
  (or (instance? incanter.core.Dataset ds)
      (and (map? ds)
           (:rows ds)
           (:column-names ds))))

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

(defn ^:no-doc extension
  "Gets the extension for the given file name as a keyword, or nil if the file has no extension"
  [f]
  (when-let [^String ext (-> f fs/extension)]
    (-> ext
        (.substring 1)
        keyword)))

(defn format-or-type [file {:keys [format]}]
  (if (#{File String} (class file))
    (or format (extension file))
    (class file)))

(defmulti ^:no-doc read-dataset*
  "Multimethod for adapter implementers to hook custom dataset readers
  into grafter.

  API users should use the front end function read-dataset instead of
  calling this."

  format-or-type)

(defmethod read-dataset* Dataset [dataset opts]
  dataset)

(defn- read-dataset-with-inferred-extension [dataset {:keys [format] :as opts}]
  (let [format (or format (extension dataset))]
    (read-dataset* (io/input-stream dataset) {:format (extension dataset)})))

(defmethod read-dataset* String [dataset opts]
  (read-dataset-with-inferred-extension dataset opts))

(defmethod read-dataset* File [dataset opts]
  (read-dataset-with-inferred-extension dataset opts))

(defmethod read-dataset* InputStream [dataset {:keys [format]}]
  (read-dataset* dataset {:format format}))

(defmethod read-dataset* ::default
  [dataset {:keys [format] :as opts}]
  (if (nil? format)
    (throw (IllegalArgumentException. (str "Please specify a format, it could not be infered when opening a dataset of type: " (class dataset))))
    (-> (io/input-stream dataset)
        (read-dataset* opts))))

(defn read-dataset
  "Opens a dataset from a datasetable thing i.e. a filename or an existing Dataset.
The multi-method dispatches based upon a :format option. If this isn't provided then
the type is used. If this isn't provided then we fallback to file extension.

Options are:

  :format - to force the datasetable to be opened with a particular method."
  [datasetable & {:keys [format] :as opts}]

  (read-dataset* datasetable opts))

(defmulti read-datasets*
  (fn [multidatasetable {:keys [format] :as opts}]
    (when (:sheet opts)
      (throw (IllegalArgumentException. "read-datasets cannot open a single sheet.  Use read-dataset* to do this.")))
    (format-or-type multidatasetable format)))

(defmethod read-datasets* clojure.lang.Sequential [datasets opts]
  datasets)

(defn read-datasets
  "Opens a lazy sequence of datasets from a something that returns multiple
  datasetables - i.e. all the worksheets in an Excel workbook."

  [dataset & {:keys [format] :as opts}]
  (read-datasets* dataset opts))

(defmulti ^:no-doc write-dataset*
  "Multi-method for adapter implementers to extend to allow
  serialising datasets into various different formats."
  (fn [destination dataset opts]
    (when-not (dataset? dataset)
      (throw (IllegalArgumentException.
              (str "Could not write dataset to" destination " as " (class dataset)
                   " is not a valid Dataset.  This error usually occurs if you try and generate tabular data from a graft"))))
    (format-or-type destination opts)))

(defmethod write-dataset* ::default [destination dataset {:keys [format] :as opts}]
  (if (nil? format)
    (throw (IllegalArgumentException. (str "Please specify a format, it could not be infered when opening a dataset of type: " (class dataset))))
    (-> (io/output-stream destination)
        (write-dataset* dataset destination opts))))

(defn write-dataset
  [destination dataset & {:keys [format] :as opts}]
  (write-dataset* destination dataset opts))

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

(defn ^:no-doc dataset->seq-of-seqs
  "Converts a dataset into a seq-of-seqs representation"
  [dataset]
  (let [col-order (:column-names dataset)
        data (:rows dataset)
        rows (map (fn [row]
                    (map (fn [item]
                           (get row item)) col-order))
                  data)
        output-data (concat [(map name col-order)] rows)]
    output-data))
