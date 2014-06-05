(ns grafter.rdf.ontologies.sdmx-concept
  (:use [grafter.rdf.ontologies.util]))

(def sdmx-concept (prefixer "http://purl.org/linked-data/sdmx/2009/concept#"))

(def sdmx-concept:statUnit (sdmx-concept "statUnit"))
(def sdmx-concept:unitMeasure (sdmx-concept "unitMeasure"))
