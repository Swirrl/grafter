(ns grafter.rdf.ontologies.sdmx-attribute
  "Some convenience terms for the SDMX attribute vocabulary."
  (:use [grafter.rdf.ontologies.util]))

(def sdmx-attribute (prefixer "http://purl.org/linked-data/sdmx/2009/attribute#"))

(def sdmx-attribute:statUnit (sdmx-attribute "statUnit"))
