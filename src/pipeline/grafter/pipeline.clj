(ns grafter.pipeline
  (:require
   [grafter.pipeline.types :refer [resolve-var create-pipeline-declaration]]))

(defonce exported-pipelines (atom {}))

(defn register-pipeline!
  "Registers the pipeline the exported pipelines."
  [name description]
  (let [pipeline (assoc description :name name)]
    (swap! exported-pipelines #(assoc % (keyword name) pipeline))))


(defrecord ^{:doc "Record representing a static pipeline declaration, i.e. one
that is declared in code."
             } DeclaredPipeline [namespace name description type
args])


(defmacro declare-pipeline
  "Declare a pipeline function, exposing it to grafter-server etc..."
  [var-name type-form metadata]
  (let [def-var (resolve-var *ns* var-name)
        decl (create-pipeline-declaration def-var type-form metadata)]
    (register-pipeline! var-name decl)
    nil))

(defn all-declared-pipelines
  ([] (all-declared-pipelines nil))
  ([type]
   (let [type? (if type
                 #(= (keyword type) (:type %))
                 identity)]

     (filter type? (sort-by (comp str :var) (vals @exported-pipelines))))))

(comment
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
