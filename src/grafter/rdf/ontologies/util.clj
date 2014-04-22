(ns grafter.rdf.ontologies.util)

(defn prefixer
  [uri-prefix]
  (fn [value]
    (str uri-prefix value)))
