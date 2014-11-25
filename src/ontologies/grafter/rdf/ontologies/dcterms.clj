(ns grafter.rdf.ontologies.dcterms
  "Some convenience variables for the dublin core vocabulary."
  (:require [grafter.rdf.ontologies.util :refer :all]))


(def dcterms (prefixer "http://purl.org/dc/terms/"))

(def dcterms:title (dcterms "title"))
(def dcterms:modified (dcterms "modified"))
(def dcterms:created (dcterms "created"))
(def dcterms:description (dcterms "description"))
(def dcterms:issued (dcterms "issued"))
(def dcterms:license (dcterms "license"))
(def dcterms:publisher (dcterms "publisher"))
(def dcterms:creator (dcterms "creator"))
(def dcterms:references (dcterms "references"))
(def dcterms:isReplacedBy (dcterms "isReplacedBy"))
(def dcterms:replaces     (dcterms "replaces"))
