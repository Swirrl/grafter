(ns grafter.pipeline
  "Functions to declare the presence of Grafter pipeline functions to
  external processes and programs such as lein-grafter and Grafter
  server."
  (:require
   [grafter.pipeline.types :refer [resolve-parameter-type create-pipeline-declaration
                                   parse-parameter]]))

(defonce ^{:doc "Map of pipelines that have been declared and exported to the pipeline runners"} exported-pipelines (atom {}))

(defn ^:no-doc register-pipeline!
  "Registers the pipeline object map with the exported pipelines."
  ([sym pipeline-obj] (register-pipeline! sym *ns* nil pipeline-obj))
  ([sym ns pipeline-obj] (register-pipeline! sym ns nil pipeline-obj))
  ([sym ns display-name pipeline-obj]
   (let [pipeline (-> pipeline-obj
                      (assoc :name sym
                             :namespace (symbol (str ns)))
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

(defn- parse-pipeline-declaration [arg-list]
  (let [[sym display-name-or-type-form] arg-list
        has-display? (string? display-name-or-type-form)
        display-name (if has-display? display-name-or-type-form nil)
        rest-args (drop (if has-display? 2 1) arg-list)
        [type-form metadata] rest-args
        opts (into {} (map vec (partition 2 2 (drop 2 rest-args))))]
    {:sym          sym
     :display-name display-name
     :type-form type-form
     :metadata metadata
     :opts opts}))

(defmacro declare-pipeline
  "Declare a pipeline function and expose it to other services such as
  the grafter leiningen plugin and grafter-server.

  declare-pipeline takes a symbol identifying the function to expose,
  an optional human friendly title string, a type-form describing the
  pipelines arguments and return type, a map of metadata describing
  each argument and an optional sequence of key-value pairs containing
  additional options. The only recognised option is :supported-operations
  which indicates whether the pipeline output supports being append to
  or deleted from the pipeline destination.

  (defn my-pipeline [a] [(->Quad a a a a)])

  (declare-pipeline my-pipeline \"My example pipeline\" [URI -> Quads]
                    {a \"Argument a\"}
                    :supported-operations #{:append :delete})

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
  ([& args]
   (let [{:keys [sym display-name type-form metadata opts]} (parse-pipeline-declaration args)]
     (if-let [sym (qualify-symbol sym)]
       (let [decl (create-pipeline-declaration sym *ns* type-form metadata opts)]
         (register-pipeline! sym *ns* display-name decl))
       (throw (ex-info (str "The symbol " sym " could not be resolved to a var.") {:error :pipeline-declaration-error
                                                                                   :sym   sym}))))
   nil))

(defn ^:no-doc all-declared-pipelines
  "List all the declared pipelines"
  ([] (all-declared-pipelines nil))
  ([type]
   (let [type? (if type
                 #(= (keyword type) (:type %))
                 identity)]

     (filter type? (sort-by (comp str :var) (vals @exported-pipelines))))))

(defn ^:no-doc coerce-arguments
  ([namespace expected-types supplied-args] (coerce-arguments namespace expected-types supplied-args {}))
  ([namespace expected-types supplied-args opts]
   (map (fn [et sa]
          (let [klass (:class et)]
            (parse-parameter (resolve-parameter-type namespace klass) sa opts))) expected-types supplied-args)))

(defn ^:no-doc coerce-pipeline-arguments
  "Coerce the arguments based on the pipelines stated types.  Receives
  a fully qualified symbol identifying the pipeline and returns the
  arguments as coerced values, or raise an error if a coercion isn't
  possible.

  Uses the multi-method grafter.pipeline.types/parse-parameter to coerce
  values."
  [pipeline-sym supplied-args]
  (let [pipeline (@exported-pipelines pipeline-sym)
        expected-types (:args pipeline)
        namespace (:namespace pipeline)]
    (coerce-arguments namespace expected-types supplied-args)))

(defn ^:no-doc execute-pipeline-with-coerced-arguments
  "Execute the pipeline specified by pipeline-sym by applying it to
  type-coerced versions of the supplied arguments.

  Expects supplied-args to be either strings or already of the
  declared data type formats."
  [pipeline-sym supplied-args]
  (let [coerced-args (coerce-pipeline-arguments pipeline-sym supplied-args)
        pipeline-fn (:var (@exported-pipelines pipeline-sym))]
    (apply pipeline-fn coerced-args)))

(defn find-pipeline
  "Find a pipeline by its fully qualified pipeline.  Accepts either a
  string or a symbol identifying the pipeline
  e.g. \"my.namespace/my-pipeline\" or 'my.namespace/my-pipeline"
  [name]
  (get @exported-pipelines (symbol name)))
