(ns grafter.tabular.common
  {:no-doc true}
  (:require [clj-excel.core :as xls]
            [grafter.sequences :as seqs]
            [clojure.java.io :as io]
            [incanter.core :as inc]
            [me.raynes.fs :as fs])
  (:import [org.apache.poi.ss.usermodel Sheet]
           [incanter.core Dataset]
           [java.io File InputStream]
           [java.net URI URL]))


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

(defn- fill-gaps-with-nil [rows headers]
  (let [blank-row (zipmap headers (repeat (count headers) nil))
        pad-with-nils (fn [row] (let [row-map (if (map? row) row (zipmap headers row))]
                                  (merge blank-row row-map)))]
    (map pad-with-nils rows)))

(defmulti to-list
  "
  Returns a list-of-lists if the given matrix is two-dimensional
  and a flat list if the matrix is one-dimensional.

  Replaces incanter's to-list with a version that doesn't hold onto the head.
  "
  type)

(defmethod to-list :incanter.core/matrix
  ([^clatrix.core.Matrix mat]
    (clatrix.core/as-vec mat)))

(defmethod to-list :incanter.core/dataset
  [data]
  (let [original-columns (vec (:column-names data))]
    (map (fn [row] (map (fn [col] (row col))
                        original-columns))
         (:rows data))))

(defmethod to-list :default [s] s)

(defmethod to-list nil [s] nil)


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
   (if (inc/dataset? data)
     data
     (let [columns (take (-> data first count) (seqs/alphabetical-column-names))]
       (make-dataset data columns))))

  ([data columns-or-f]
   (let [original-meta (meta data)
         data-seq (if (inc/dataset? data) (to-list data) data)
         [column-headers rows] (if (fn? columns-or-f)
                                 (columns-or-f data-seq)
                                 [columns-or-f data-seq])
         full-data (fill-gaps-with-nil rows column-headers)]
     (-> (inc/dataset column-headers full-data)
         (with-meta original-meta)))))

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
  (let [original-meta (meta dataset)
        original-columns (column-names dataset)]
    (-> (make-dataset (->> dataset :rows f)
                      original-columns)
        (with-meta original-meta))))

(defn ^:no-doc extension
  "Gets the extension for the given file name as a keyword, or nil if the file has no extension"
  [f]
  (when-let [^String ext (-> f fs/extension)]
    (-> ext
        (.substring 1)
        keyword)))

(defn format-or-type [ds {:keys [format]}]
  (if (#{File String} (class ds))
    (or format (extension ds))
    (class ds)))

(defn- get-format [source {:keys [format] :as opts}]
  format)

(defn assoc-data-source-meta [output-ds data-source]
  "Adds metadata about where the dataset was loaded from to the object."
  (let [source-meta (cond
                      (#{String File URI URL} (class data-source))  {:grafter.tabular/data-source data-source}
                      (instance? incanter.core.Dataset data-source) (meta data-source)
                      :else {:grafter.tabular/data-source :datasource-unknown}) ]
    (with-meta output-ds (merge (meta output-ds) source-meta))))

(defmulti ^:no-doc read-dataset*
  "Multimethod for adapter implementers to hook custom dataset readers
  into grafter.

  API users should use the front end function read-dataset instead of
  calling this."

  get-format)

(defprotocol DatasetFormat
  "Represents a type from which it may be possible to infer the format
  of the contained data."
  (infer-format [source]
    "Attempt to infer the data format of the given source. Should
    return a keyword if the format was inferred, or nil if the
    inference failed."))

(extend-protocol DatasetFormat
  String
  (infer-format [s] (extension s))

  File
  (infer-format [f] (extension (.getName f)))

  java.net.URL
  (infer-format [url] (extension (.getPath url)))

  java.net.URI
  (infer-format [uri] (extension (.getPath uri)))

  nil
  (infer-format [_] nil))

(defn- ^:no-doc infer-format-of
  "Attempt to infer the format of the given data source. Returns nil
  if the format could not be inferred."
  [source]
  (if (satisfies? DatasetFormat source)
    (infer-format source)))

(defmulti read-dataset-source
  "Opens a dataset from a datasetable thing e.g. a filename or an existing Dataset.
  The multi-method dispatches based upon the type of the source.

  Supplied options are passed to the individual handler methods and they may
  have their own requirements on the options provided."
  ;; NOTE: This is not a protocol, because protocols don't give you a :default
  ;; option for dispatch.
  (fn [src opts]
    (class src)))

(defmulti write-dataset-source
  "Writes a dataset from a datasetable thing e.g. a filename or an existing Dataset.
  The multi-method dispatches based upon the type of the source.

  Supplied options are passed to the individual handler methods and they may
  have their own requirements on the options provided."
  ;; NOTE: This is not a protocol, because protocols don't give you a :default
  ;; option for dispatch.
  (fn [dest ds opts]
    (class dest)))

(defn- ^:no-doc dispatch-with-format-option
  "Takes a function to call, a data source and an options hash containing an
  optional :format key.

  If :format is not provided then an attempt will be made to infer it from the
  data source via the DatasetFormat protocol.

  Once the format is resolved it then the target function is called with the
  data source and options map."
  [f source {:keys [format] :as opts}]
  (if-let [format (or format (infer-format-of source))]
    (f source (assoc opts :format format))
    (throw (IllegalArgumentException. (str "Please specify a format, it could not be inferred from the source: " source)))))

(defmethod read-dataset-source Dataset [ds opts] ds)

(defmethod read-dataset-source :default [source opts]
  (dispatch-with-format-option read-dataset* source opts))

(defn read-dataset [source & {:as opts}]
  (-> (read-dataset-source source opts)
      (assoc-data-source-meta source)))

(defmulti read-datasets-source
  "Reads a sequence of datasets from a given data source given a map of
  options. Dispatches on the type of the data source.

  NOTE: implementations for different source types may have different
  requirements for the provided options."
  (fn [source {:keys [sheet] :as opts}]
    (when sheet
      (throw (IllegalArgumentException. "read-datasets cannot open a single sheet. Use read-dataset* to do this.")))
    (class source)))

(defmulti read-datasets*
  get-format)

(defmethod read-datasets-source clojure.lang.Sequential [datasets opts]
  datasets)

(defmethod read-datasets-source :default [source opts]
  (dispatch-with-format-option read-datasets* source opts))

(defn read-datasets
  "Opens a lazy sequence of datasets from a something that returns multiple
  datasetables - i.e. all the worksheets in an Excel workbook."

  [dataset & {:keys [format] :as opts}]
  (read-datasets-source dataset opts))

(defmulti ^:no-doc write-dataset*
  "Multi-method for adapter implementers to extend to allow
  serialising datasets into various different formats."
  (fn [destination dataset opts]
    (when-not (dataset? dataset)
      (throw (IllegalArgumentException.
              (str "Could not write dataset to " destination " as " (class dataset)
                   " is not a valid Dataset.  This error usually occurs if you try and generate tabular data from a graft"))))
    (get-format destination opts)))

(defn- ^:no-doc dispatch-write-with-format-option
  "Same as above but for writer - so it takes an additional argument"
  [f dest ds {:keys [format] :as opts}]
  (if-let [format (or format (infer-format-of dest))]
    (f dest ds (assoc opts :format format))
    (throw (IllegalArgumentException. (str "Please specify a format, it could not be inferred from the destination: " dest)))))

(defmethod write-dataset-source :default [dest ds opts]
  (dispatch-write-with-format-option write-dataset* dest ds opts))

(defmethod write-dataset-source Dataset [dest ds opts] ds)

(defn write-dataset
  [destination dataset & {:keys [format] :as opts}]
  (write-dataset-source destination dataset opts))

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

(defn ^:no-doc resolve-col-id [column-key headers not-found]
  (let [converted-column-key (cond
                              (string? column-key) (keyword column-key)
                              (keyword? column-key) (name column-key)
                              (integer? column-key) (nth headers column-key not-found))]
    (if-let [val (some #{column-key converted-column-key} headers)]
      val
      not-found)))

(defn resolve-column-id
  "Finds and resolves the column id by converting between symbols and
  strings.  If column-key is not found in the datsets headers then
  not-found is returned."

  ([dataset column-key] (resolve-column-id dataset column-key nil))
  ([dataset column-key not-found]

   (let [headers (column-names dataset)]
     (resolve-col-id column-key headers not-found))))

(defn ^:no-doc map-keys [f hash]
  "Apply f to the keys in the supplied hashmap and return a new
  hashmap."
  (zipmap (map f (keys hash))
          (vals hash)))

(defn ^:no-doc lift->vector [x]
  "Lifts singular values into a sequential collection. If the given argument is sequential then it is returned, otherwise a sequential
   container containing the value is returned."
  (if (sequential? x) x [x]))

(defmacro register-format-alias
  "Register an extra format alias to be handled by a root multi-method (either
  read-dataset*, read-datasets* or write-dataset*.

  This works by building defmethod definitions that delegate to the root-key
  dispatch value for each of the supplied aliases."
  [multi-fn-symbol root-key alias]

  (let [args 'args]
    `(defmethod ~multi-fn-symbol ~alias [& ~args]
       (let [opts# (merge (last ~args) {:format ~root-key})]
         (apply ~multi-fn-symbol (concat (drop-last ~args) [opts#]))))))
