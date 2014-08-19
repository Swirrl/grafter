(ns grafter.rdf.ontologies.util
  "Some utility functions for ontology specification and management
  within Clojure.")

(defn prefixer
  [uri-prefix]
  (fn [value]
    (str uri-prefix value)))
