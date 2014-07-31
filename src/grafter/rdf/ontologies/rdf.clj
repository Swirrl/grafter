(ns grafter.rdf.ontologies.rdf
  (:use grafter.rdf.ontologies.util))

(def rdf (prefixer "http://www.w3.org/1999/02/22-rdf-syntax-ns#"))
(def rdfs (prefixer "http://www.w3.org/2000/01/rdf-schema#"))

(def rdf:a (rdf "type"))

(def rdf:Property (rdf "Property"))
(def rdfs:subPropertyOf (rdfs "subPropertyOf"))

(def rdfs:Class (rdfs "Class"))
(def rdfs:subClassOf (rdfs "subClassOf"))

(def rdfs:label (rdfs "label"))
(def rdfs:comment (rdfs "comment"))
(def rdfs:isDefinedBy (rdfs "isDefinedBy"))
(def rdfs:range (rdfs "range"))
(def rdfs:domain (rdfs "domain"))
