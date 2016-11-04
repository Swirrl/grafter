(ns grafter.pipeline.types
  "This namespace code for parsing and interpreting
  grafter.pipeline/declare-pipeline type signatures.  In particular it
  defines a macro deftype-reader that can be used to coerce/read
  strings into their appropriate clojure types.

  We use the declared pipelines signature to guide the interpretation
  of a string the target type."
  (:require [clojure.data :refer [diff]]
            [clojure.edn :as edn]
            [clojure.instant :refer [read-instant-date]]
            [clojure.set :as set]
            [grafter.tabular :as tabular]
            [clojure.string :as str])
  (:import [java.net URI URL]
           [java.util UUID Date Map]
           [incanter.core Dataset]))

;; NOTE: we could possibly have done this in a slightly more clojurey
;; way by defining tagged literals and supplying :readers for them to
;; clojure.edn/read-string, however we'd still need to define a
;; mechanism for extension.
;;
;; This mechanism has one (minor) advantage to the approach above
;; which is that when supplying arguments to a pipeline you don't need
;; to provide the tag itself, just the form to interpret, as we
;; already know the target type from the pipelines declaration.


(defmulti ^:no-doc type-reader (fn [target-type input-value]
                                 [target-type (type input-value)]))

(defmulti ^:no-doc can-parse-type? identity)

(defmethod can-parse-type? :default [v]
  false)

(defmacro deftype-reader
  "Define a multimethod for reliably coercing types into a target type.

  Additionally extends the predicate method can-parse-type? to return
  true for the newly added type."
  [[output-type# input-type#] [output-type-arg input-value] form]
  `(do
     (defmethod type-reader [~output-type# ~input-type#] [~output-type-arg ~input-value]
       (let [output-value# ~form]
         (if (instance? ~output-type# output-value#)
           output-value#
           (throw (IllegalArgumentException. (str "Could not coerce value " ~input-value " into type " (.getName ~output-type#)))))))

     ;; Define a fall-through for already coerced types
     (defmethod type-reader [~output-type# ~output-type#] [~output-type-arg ~input-value]
       ~input-value)

     (defmethod can-parse-type? ~output-type# [val#] true)))

(deftype-reader [Number String] [ov input-value]
  (edn/read-string input-value))

(deftype-reader [Float String] [ov input-value]
  (Float/parseFloat input-value))

(deftype-reader [Double String] [ov input-value]
  (Double/parseDouble input-value))

(deftype-reader [Byte String] [_ value]
  (Byte/parseByte value))

(deftype-reader [Integer String] [_ value]
  (Integer/parseInt value))

(deftype-reader [Long String] [_ value]
  (Long/parseLong value))

(deftype-reader [clojure.lang.BigInt String] [_ value]
  (Long/parseLong value))

(deftype-reader [Boolean String] [_ value]
  (Boolean/parseBoolean value))

(deftype-reader [String String] [_ value]
  value)

(deftype-reader [Map String] [_ value]
  (edn/read-string value))

(deftype-reader [URI String] [_ value]
  (java.net.URI. value))

(deftype-reader [URL String] [_ value]
  (java.net.URL. value))

(deftype-reader [UUID String] [_ value]
  (UUID/fromString value))

(deftype-reader [Date String] [_ value]
  (read-instant-date value))

(deftype-reader [clojure.lang.Keyword String] [_ value]
  (keyword (if (> (.length value) 1)
             (let [fchar (.substring value 0 1)]
               (if (= ":" fchar)
                 (.substring value 1)
                 value))
             value)))

(deftype-reader [incanter.core.Dataset String] [_ value]
  (tabular/read-dataset value))

(defmethod type-reader :default [target-type input-value]
  (throw (ex-info (str "No grafter.pipeline.types/type-reader defined to coerce values of type " (type input-value) " into " target-type)
                  {:error :type-reader-error})))

(defn ^:no-doc coerce-arguments [expected-types supplied-args]
  (map (fn [et sa]
         (type-reader (:class et) sa)) expected-types supplied-args))

;;[Symbol] -> {:arg-types [Symbol], :return-type Symbol]}
(defn ^:no-doc parse-type-list
  "Parses a given list of symbols expected to represent a pipeline
  type definition. Validates the list has the expected format - n >= 0
  parameter types followed by a '-> followed by the return
  type. Returns a record containing the ordered list of parameter
  symbols and the return type symbol."
  [l]
  (let [c (count l)]
    (if (< c 2)
      (throw (IllegalArgumentException. "Invalid type declaration - requires at least -> and return type"))
      (let [arg-count (- c 2)
            separator (l arg-count)
            return-type (last l)
            arg-types (vec (take arg-count l))]

        (if (= '-> separator)
          {:arg-types arg-types :return-type return-type}
          (throw (IllegalArgumentException. "Invalid type declaration: Expected [args -> return-type")))))))

;;[a] -> [b] -> ()
(defn- validate-argument-count
  "Throws an exception if the number of parameter types in a pipeline
  type declaration do not match the number of elements in the pipeline
  var's argument list."
  [declared-arg-list type-list]
  (let [comp (compare (count declared-arg-list) (count type-list))]
    (if (not= 0 comp)
      (let [det (if (pos? comp) "few" "many")
            msg (str "Too " det " argument types provided for pipeline argument list " declared-arg-list " (got " type-list ")")]
        (throw (IllegalArgumentException. msg))))))

;;Symbol -> Class
(defn- resolve-parameter-type
  "Attempts to resolve a symbol representing a pipeline parameter type
  to the class instance representing the class. Throws an exception if
  the resolution fails."
  [sym]
  (if-let [cls (resolve sym)]
    cls
    (throw (IllegalArgumentException. (str "Failed to resolve " sym " to class")))))

(defn- get-arg-descriptor [name-sym type-sym doc doc-meta]
  (let [param-class (resolve-parameter-type type-sym)
        common {:name name-sym :class param-class :doc doc}]
    (if (can-parse-type? param-class)
      (if doc-meta
        (assoc common :meta doc-meta)
        common)
      (throw (IllegalArgumentException. (str "Unsupported pipeline parameter type: " param-class))))))

;;Namespace -> Symbol -> Var
(defn ^:no-doc resolve-var
  "Attempts to resolve a named var inside the given namespace. Throws
  an exception if the resolution fails."
  [ns v]
  (if-let [rv (ns-resolve ns v)]
    rv
    (throw (IllegalArgumentException. (str "Cannot resolve var " v " in namespace " (.getName ns))))))

;;Symbol -> PipelineType
(defn- pipeline-type-from-return-type-sym
  "Infers the 'type' (graft or pipe) of a pipeline function from its
  return type. Throws an exception if the return type symbol is
  invalid."
  [ret-sym]
  (condp = ret-sym
    '(Seq Statement) :graft ;; deprecated
    '(Seq Quad) :graft
    '[Quad] :graft
    'Quads :graft
    'Dataset :pipe
    (let [msg (str "Invalid return type " ret-sym " for pipeline function: required Dataset or [Quad]")]
      (throw (IllegalArgumentException. msg)))))

;;[a] -> {a b} -> [[a b]]
(defn- correlate-pairs
  [ordered-keys m]
  "Orders the pairs in a map so the keys are in the same order as the
  elements in the given 'reference' vector.
  (correlate-pairs [:b :a] {:a 1 :b 2}) => [[:b 2] [:a 1]]"
  {:pre [(= (count ordered-keys) (count m))
         (= (set ordered-keys) (set (keys m)))]}
  (let [indexes (into {} (map-indexed #(vector %2 %1) ordered-keys))]
    (vec (sort-by (comp indexes first) m))))


;;[Symbol] -> [Symbol] -> {Symbol String} -> [ArgDescriptor]
(defn- resolve-pipeline-arg-descriptors [arg-names arg-type-syms doc-map]
  (validate-argument-count arg-names arg-type-syms)
  (let [[missing-doc unknown-doc _] (diff (set arg-names) (set (keys doc-map)))]
    (cond
     (not (empty? missing-doc))
     (throw (IllegalArgumentException. (str "No documentation found for variable(s): " missing-doc)))

     (not (empty? unknown-doc))
     (throw (IllegalArgumentException. (str "Found documentation for unknown variable(s): " unknown-doc)))

     :else
     (let [correlated-docs (correlate-pairs arg-names doc-map)]
       (mapv (fn [n ts [doc-name doc]] (get-arg-descriptor doc-name ts doc (-> (meta doc-name)
                                                                              ;; remove line number metadata inserted by clojure as its superfluous here
                                                                              (dissoc :file :line :column))))
             arg-names
             arg-type-syms
             correlated-docs)))))

(defn- get-supported-pipeline-operations [{:keys [supported-operations]}]
  (if (some? supported-operations)
    (let [ops (set supported-operations)
          valid-operations #{:append :delete}
          invalid-operations (set/difference ops valid-operations)]
      (if (empty? invalid-operations)
        ops
        (throw (IllegalArgumentException. (str "Invalid supported operations for pipeline: "
                                               (str/join ", " invalid-operations)
                                               ". Valid operations are: " (str/join ", " valid-operations))))))
    #{:append}))

;;Var -> [Symbol] -> Metadata -> PipelineDef
(defn ^:no-doc create-pipeline-declaration
  "Inspects a var containing a pipeline function along with an
  associated type definition and metadata map. The type definition
  should declare the type of each parameter and the return type of the
  pipeline. The metadata map must contain a key-value pair for each
  named parameter in the pipeline function argument list. The value
  corresponding to each key in the metadata map is expected to be a
  String describing the parameter. The metadata map can also contain
  an optional :supported-operations key associated to a collection
  containing :append and/or :delete. These operations indicate whether
  the data returned from the pipeline can be appended to or deleted
  from the destination."
  [sym type-list metadata]
  (let [def-var (resolve-var *ns* sym)
        def-meta (meta def-var)
        arg-list (first (:arglists def-meta))
        {:keys [arg-types return-type]} (parse-type-list type-list)
        pipeline-type (pipeline-type-from-return-type-sym return-type)
        supported-operations (get-supported-pipeline-operations metadata)
        args (resolve-pipeline-arg-descriptors arg-list arg-types (dissoc metadata :supported-operations))]
     {:var def-var
      :doc (or (:doc def-meta) "")
      :args args
      :type pipeline-type
      :declared-args arg-list
      :operations supported-operations}))
