(ns grafter.pipeline
  {:no-doc true})

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
          ))
      ))
