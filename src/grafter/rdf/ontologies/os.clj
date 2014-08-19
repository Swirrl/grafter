(ns grafter.rdf.ontologies.os
  "Some convenience terms for the ordnancesurvey postcodes
  vocabulary."
  (:use grafter.rdf.ontologies.util))

(def os (prefixer "http://data.ordnancesurvey.co.uk/ontology/postcode/"))
(def os:postcode (os "postcode"))
