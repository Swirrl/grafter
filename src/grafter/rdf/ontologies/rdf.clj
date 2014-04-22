(ns grafter.rdf.ontologies.rdf
  (:use grafter.rdf.ontologies.util))

(def rdf (prefixer "http://www.w3.org/1999/02/22-rdf-syntax-ns#"))
(def rdfs (prefixer "http://www.w3.org/2000/01/rdf-schema#"))

(def rdf:a (rdf "type"))

(def rdfs:Class (rdfs "Class"))
(def rdfs:label (rdfs "label"))
