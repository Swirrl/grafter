(ns ^:no-doc grafter.rdf.ontologies.util
  "Some utility functions for ontology specification and management
  within Clojure.

  For internal use, mostly to prevent circular dependencies from the
  ontology namespaces on grafter.rdf/prefixer")

(defn prefixer
  "Takes the base prefix of a URI string and returns a function that
  concatenates its argument onto the end of it e.g.
  ((prefixer \"http://example.org/\") \"foo\") ;; => \"http://example.org/foo\""
  [uri-prefix]
  (fn [value]
    (str uri-prefix value)))
