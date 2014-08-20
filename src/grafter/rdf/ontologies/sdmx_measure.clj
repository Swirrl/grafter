(ns grafter.rdf.ontologies.sdmx-measure
  "Some convenience terms for the SDMX measure vocabulary."
  (:use [grafter.rdf.ontologies.util]))

(def sdmx-measure (prefixer "http://purl.org/linked-data/sdmx/2009/measure#"))

(def sdmx-measure:obsValue (sdmx-measure "obsValue"))
