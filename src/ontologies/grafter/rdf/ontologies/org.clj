(ns grafter.rdf.ontologies.org
  (:require [grafter.rdf.ontologies.util :refer :all]))

(def org (prefixer "http://www.w3.org/ns/org#"))

(def org:Organization (org "Organization"))
