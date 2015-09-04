(ns grafter.pipeline.types
  (:require [clojure.set :as s]
            [clojure.string :as str]
            [clojure.data :refer [diff]]
            [clojure.edn :as edn]
            [grafter.tabular :as tabular]
            [grafter.tabular.common :as tabcom])
    (:import [java.net URI]
           [java.util UUID Date]
           [clojure.lang Keyword]
           [incanter.core Dataset]))


;;Multipart -> Dataset
(defn file-part->dataset [{:keys [tempfile filename]}]
  (if (nil? filename)
    (throw (RuntimeException. "'filename' attribute required for pipeline parameter file part"))
    (if-let [format (tabcom/extension filename)]
      (tabular/read-dataset tempfile :format format)
      (throw (RuntimeException. (str "Cannot infer format for file '" filename "' as it has no extension"))))))

;;(Any -> Bool) -> String -> (String -> Any)
(defn- reader-for-predicate
  "Returns a function which reads an input string as EDN and throws an
  exception if the given validation predicate fails for the parsed
  value."
  [validate-type-fn type-name]
  (fn [s]
    (let [r (edn/read-string s)]
      (if (validate-type-fn r)
        r
        (throw (IllegalArgumentException. (str "Cannot read " s " as a " type-name)))))))

;;Class[T] -> (String -> T)
(defn- reader-for-type
  "Returns a function for parsing EDN strings into instances of the
  given class."
  [cls]
  (reader-for-predicate #(instance? cls %) (.getName cls)))

(defn- read-uri [part]
  (let [s ((reader-for-type String) part)]
    (try
      (URI. s)
      (catch Exception ex
        (throw (IllegalArgumentException. "Invalid format for URI"))))))

(def ^:private type-readers
  {Boolean (reader-for-type Boolean)
   String (reader-for-type String)
   URI read-uri
   incanter.core.Dataset file-part->dataset})

;;Class -> Bool
(defn- can-parse-type?
  "Whether the given class is a supported pipeline parameter type."
  [cls]
  (contains? type-readers cls))

;;Class[T] -> RequestPart -> T
(defn parse-arg
  "Parses a ring request part into an instance of the given target
  class. Throws an exception if the conversion fails."
  [cls request-part]
  (if-let [parse-fn (get type-readers cls)]
    (parse-fn request-part)
    (throw (RuntimeException. (str "No handler found for type" cls)))))

;;[Symbol] -> {:arg-types [Symbol], :return-type Symbol]}
(defn parse-type-list
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
  (if-let [cls (ns-resolve (find-ns 'grafter.pipeline.types) sym)]
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
(defn resolve-var
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
    '(Seq Statement) :graft
    'Dataset :pipe
    (let [msg (str "Invalid return type " ret-sym " for pipeline function: required Dataset or (Seq Statement)")]
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
       (mapv (fn [n ts [doc-name doc]] (get-arg-descriptor doc-name ts doc (meta doc-name)))
             arg-names
             arg-type-syms
             correlated-docs)))))

;;Var -> [Symbol] -> Metadata -> PipelineDef
(defn create-pipeline-declaration
  "Inspects a var containing a pipeline function along with an
  associated type definition and metadata map. The type definition
  should declare the type of each parameter and the return type of the
  pipeline. The metadata map must contain a key-value pair for each
  named parameter in the pipeline function argument list. The value
  corresponding to each key in the metadata map is expected to be a
  String describing the parameter."
  [def-var type-list metadata]
  (let [def-meta (meta def-var)
        arg-list (first (:arglists def-meta))
        {:keys [arg-types return-type]} (parse-type-list type-list)
        pipeline-type (pipeline-type-from-return-type-sym return-type)
        args (resolve-pipeline-arg-descriptors arg-list arg-types metadata)]
     {:var def-var
      :doc (or (:doc def-meta) "")
      :args args
      :type pipeline-type
      :declared-args arg-list}))
