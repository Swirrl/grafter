(ns grafter.rdf.ontologies.void
  "Some convenience terms for the VOID vocabulary."
  (:use grafter.rdf.ontologies.util))

(def void (prefixer "http://rdfs.org/ns/void#"))

(def void:Dataset (void "Dataset"))
(def void:dataDump (void "dataDump"))
(def void:sparqlEndpoint (void "sparqlEndpoint"))
(def void:triples (void "triples"))
