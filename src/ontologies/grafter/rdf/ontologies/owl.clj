(ns grafter.rdf.ontologies.owl
  "Some convenience terms for the owl vocabulary."
  (:require [grafter.rdf.ontologies.util :refer :all]))

(def owl (prefixer "http://www.w3.org/2002/07/owl#"))

(def owl:Ontology (owl "Ontology"))

(def owl:Class (owl "Class"))
