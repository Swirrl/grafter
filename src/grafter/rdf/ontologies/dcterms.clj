(ns grafter.rdf.ontologies.dcterms
  "Some convenience variables for the dublin core vocabulary."
  (:use [grafter.rdf.ontologies.util]))


(def dcterms (prefixer "http://purl.org/dc/terms/"))

(def dcterms:title (dcterms "title"))
(def dcterms:modified (dcterms "modified"))
(def dcterms:created (dcterms "created"))
(def dcterms:description (dcterms "description"))
(def dcterms:issued (dcterms "issued"))
(def dcterms:license (dcterms "license"))
(def dcterms:publisher (dcterms "publisher"))
(def dcterms:references (dcterms "references"))
