(ns grafter.rdf.ontologies.skos
  "Some convenience terms for the SKOS vocabulary."
  (:use [grafter.rdf.ontologies.util]))

(def skos (prefixer "http://www.w3.org/2004/02/skos/core#"))

(def skos:ConceptScheme (skos "ConceptScheme"))
(def skos:hasTopConcept (skos "hasTopConcept"))

(def skos:Concept (skos "Concept"))
(def skos:inScheme (skos "inScheme"))
