(ns grafter.rdf.ontologies.sdmx-concept
  "Some convenience terms for the SDMX concept vocabulary"
  (:require [grafter.rdf.ontologies.util :refer :all]))

(def sdmx-concept (prefixer "http://purl.org/linked-data/sdmx/2009/concept#"))

(def sdmx-concept:statUnit (sdmx-concept "statUnit"))
(def sdmx-concept:unitMeasure (sdmx-concept "unitMeasure"))
