(ns grafter.rdf.ontologies.dcat
  (:require [grafter.rdf.ontologies.util :refer :all]))

(def dcat (prefixer "http://www.w3.org/ns/dcat#"))

(def dcat:Dataset (dcat "Dataset"))
(def dcat:theme (dcat "theme"))
