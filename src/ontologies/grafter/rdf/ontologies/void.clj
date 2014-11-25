(ns grafter.rdf.ontologies.void
  "Some convenience terms for the VOID vocabulary."
  (:require [grafter.rdf.ontologies.util :refer :all]))

(def void (prefixer "http://rdfs.org/ns/void#"))

(def void:Dataset (void "Dataset"))
(def void:dataDump (void "dataDump"))
(def void:sparqlEndpoint (void "sparqlEndpoint"))
(def void:triples (void "triples"))
(def void:vocabulary (void "vocabulary"))
