(ns grafter.rdf.ontologies.dcat
  (:use [grafter.rdf.ontologies.util]))

(def dcat (prefixer "http://www.w3.org/ns/dcat#"))

(def dcat:Dataset (dcat "Dataset"))
(def dcat:theme (dcat "theme"))
