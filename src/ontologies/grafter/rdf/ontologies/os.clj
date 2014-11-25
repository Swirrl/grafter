(ns grafter.rdf.ontologies.os
  "Some convenience terms for the ordnancesurvey postcodes
  vocabulary."
  (:require [grafter.rdf.ontologies.util :refer :all]))

(def os (prefixer "http://data.ordnancesurvey.co.uk/ontology/postcode/"))
(def os:postcode (os "postcode"))
