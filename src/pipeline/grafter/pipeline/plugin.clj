(ns grafter.pipeline.plugin
  {:no-doc true}
  (:require [clojure.string :as string]
            [grafter.pipeline :refer [fully-qualified-name all-pipelines-on-classpath
                                      all-pipes-on-classpath all-grafts-on-classpath]]))

;; NOTE
;;
;; This code exists here for lein-grafter, as the plugins code doesn't
;; run in the context of the project.  Putting this code here
;; dramatically simplifies things as the plugin needs to use
;; syntax-quote to eval-in-project.  The less code in the syntax quote
;; form the better!

(defn collapse-whitespace [s]
  (string/replace s #"(\n| )+" " "))

(defn- format-pipeline [pipeline]
  (let [pattern "%1$-60s %2$-30s %3$s"
        data (into-array Object
                         [(fully-qualified-name pipeline)
                          (if-let [args (:args pipeline)]
                            (string/join ", " args)
                            "???")
                          (if-let [doc (collapse-whitespace (:doc pipeline))]
                            (str ";; " doc)
                            ";; No doc string")])]
    (String/format pattern data)))

(defn list-pipelines []
  (map format-pipeline (all-pipelines-on-classpath)))

(defn list-pipes []
  (map format-pipeline (all-pipes-on-classpath)))

(defn list-grafts []
  (map format-pipeline (all-grafts-on-classpath)))
