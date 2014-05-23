(ns grafter.rdf.ontologies.qb
  (:use [grafter.rdf.ontologies.util]))

(def qb (prefixer "http://purl.org/linked-data/cube#"))

(def qb:dataSet (qb "dataSet"))

(def qb:Observation (qb "Observation"))

(def qb:DataStructureDefinition (qb "DataStructureDefinition"))
