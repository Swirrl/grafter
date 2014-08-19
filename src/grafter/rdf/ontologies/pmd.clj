(ns grafter.rdf.ontologies.pmd
  "Some convenience terms for the Publish My Data vocabulary."
  (:use [grafter.rdf.ontologies.util]))

(def pmd (prefixer "http://publishmydata.com/def/dataset#"))


(def pmd:contactEmail (pmd "contactEmail"))
(def pmd:graph (pmd "graph"))

(def pmd:Dataset (pmd "Dataset"))
