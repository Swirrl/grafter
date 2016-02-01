(ns grafter.pipeline
  (:require
   [grafter.pipeline.types :refer [resolve-var create-pipeline-declaration
                                   coerce-arguments]]))

(defonce ^{:doc "Map of pipelines that have been declared and exported to the pipeline runners"} exported-pipelines (atom {}))

(defn ^:no-doc register-pipeline!
  "Registers the pipeline the exported pipelines."
  ([sym description] (register-pipeline! sym nil description))
  ([sym display-name description]
   (let [pipeline (-> description
                      (assoc :name sym)
                      (cond-> display-name (assoc :display-name display-name)))]
     (swap! exported-pipelines #(assoc % sym pipeline)))))

(defn ^:no-doc qualify-symbol
  "Returns a fully qualified name for the supplied symbol or string or nil if
  it's not found."
  [sym]
  (let [resolved-symbol (resolve (symbol sym))]
    (when resolved-symbol
      (let [ns (->> resolved-symbol meta :ns)]
        (if (re-find #"\/" (str sym))
          (symbol sym)
          (symbol (str ns "/" sym)))))))

(defmacro declare-pipeline
  "Declare a pipeline function, exposing it to grafter-server etc..."

  ([sym display-name type-form metadata]
   (if-let [sym (qualify-symbol sym)]
     (let [decl (create-pipeline-declaration sym type-form metadata)]
       (register-pipeline! sym display-name decl))
     (throw (ex-info (str "The symbol " sym " could not be resolved to a var.") {:error :pipeline-declaration-error
                                                                                 :sym sym})))
   nil)

  ([sym type-form metadata]
   `(declare-pipeline ~sym nil ~type-form ~metadata)))

(defn ^:no-doc all-declared-pipelines
  "List all the declared pipelines"
  ([] (all-declared-pipelines nil))
  ([type]
   (let [type? (if type
                 #(= (keyword type) (:type %))
                 identity)]

     (filter type? (sort-by (comp str :var) (vals @exported-pipelines))))))

(defn ^:no-doc coerce-pipeline-arguments
  "Coerce the arguments based on the pipelines stated types.  Receives
  a fully qualified symbol identifying the pipeline and returns the
  arguments as coerced values, or raise an error if a coercion isn't
  possible.

  Uses the multi-method grafter.pipeline.types/type-reader to coerce
  values."
  [pipeline-sym supplied-args]
  (let [expected-types (:args (@exported-pipelines pipeline-sym))]
    (coerce-arguments expected-types supplied-args)))

(defn ^:no-doc execute-pipeline-with-coerced-arguments
  "Execute the pipeline specified by pipeline-sym by applying it to
  type-coerced versions of the supplied arguments.

  Expects supplied-args to be either strings or already of the
  declared data type formats."
  [pipeline-sym supplied-args]
  (let [coerced-args (coerce-pipeline-arguments pipeline-sym supplied-args)
        pipeline-fn (:var (@exported-pipelines pipeline-sym))]
    (apply pipeline-fn coerced-args)))
