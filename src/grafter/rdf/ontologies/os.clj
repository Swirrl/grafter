(ns grafter.rdf.ontologies.os
  (:use grafter.rdf.ontologies.util))

(def os (prefixer "http://data.ordnancesurvey.co.uk/ontology/postcode/"))
(def os:postcode (os "postcode"))
