(ns grafter.pipeline
  "Functions to declare the presence of Grafter pipeline functions to
  external processes and programs such as lein-grafter and Grafter
  server."
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
  "Declare a pipeline function and expose it to other services such as
  the grafter leiningen plugin and grafter-server.

  declare-pipeline takes a symbol identifying the function to expose,
  an optional human friendly title string a type-form describing the
  pipelines arguments and return type and a map of metadata describing
  each argument.

  (defn my-pipeline [a] [(->Quad a a a a)])

  (declare-pipeline my-pipeline \"My example pipeline\" [URI -> Quads]
                    {a \"Argument a\"})

  Note that the type-form/signature specifies the input arguments
  followed by a -> and an output type.

  All input argument types MUST be imported into the namespace and
  have a type reader declared via
  grafter.pipeline.types/deftype-reader.

  Output types do not need be imported into the namespace, and must
  either be the symbols Quads or Dataset, or an alias such as
  \"[Quad]\".

  Default type-readers are defined for common grafter/clojure types."
  {:style/indent :defn}
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
