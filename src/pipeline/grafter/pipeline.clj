(ns grafter.pipeline
  (:require
   [grafter.pipeline.types :refer [resolve-var create-pipeline-declaration]]))

(defonce exported-pipelines (atom {}))

(defn register-pipeline!
  "Registers the pipeline the exported pipelines."
  [sym display-name description]
  (let [pipeline (-> description
                     (assoc :name sym)
                     (cond-> display-name (assoc :display-name display-name)))]
    (swap! exported-pipelines #(assoc % sym pipeline))))

(defn qualify-symbol
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
     (throw (ex-info (str "The symbol " sym " could not be resolved to a var.") {:type :pipeline-declaration-error
                                                                                 :sym sym})))
   nil)

  ([sym type-form metadata]
   `(declare-pipeline ~sym nil ~type-form ~metadata)))

(defn all-declared-pipelines
  ([] (all-declared-pipelines nil))
  ([type]
   (let [type? (if type
                 #(= (keyword type) (:type %))
                 identity)]

     (filter type? (sort-by (comp str :var) (vals @exported-pipelines))))))

(comment

  ;; TODO consider distinguishing between these...
  (defrecord ^{:doc "Record representing a static pipeline declaration, i.e. one
that is declared in code."
               } DeclaredPipeline [namespace name description type
                                   args])




  (defrecord ^{:doc "Record representing a pipeline application.  It is
    effectively a pipeline function with its arguments applied that should be
    executed within a specified binding."}

      Application [function parameters binding-map]

      clojure.lang.IDeref

      (deref [this]
        (if binding-map
          (with-bindings binding-map
            (apply function parameters)
            )))))
