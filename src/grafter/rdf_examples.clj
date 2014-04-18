(ns grafter.rdf-examples
  (:use [grafter.rdf]
        [grafter.rdf.sesame]
        [grafter.csv :as csv])
  (:require [grafter.rdf.protocols :as pr]))

(def rdf (prefixer "http://www.w3.org/1999/02/22-rdf-syntax-ns#"))
(def rdfs (prefixer "http://www.w3.org/2000/01/rdf-schema#"))
(def qb (prefixer "http://purl.org/linked-data/cube#"))
(def glasgow (prefixer "http://linked.glasgow.gov.uk/def/"))
(def sdmxmeasure (prefixer "http://purl.org/linked-data/sdmx/2009/measure#"))
(def xsd (prefixer "http://www.w3.org/2001/XMLSchema#"))
(def owl (prefixer "http://www.w3.org/2002/07/owl#"))
(def skos (prefixer "http://www.w3.org/2004/02/skos/core#"))

(def urbanassets (prefixer "http://linked.glasgow.gov.uk/def/urban-assets/"))

(defonce my-repo (-> "./tmp/grafter-sesame-store" native-store repo))

(defn add-triples [repo]
  (pr/add repo (expand-subject ["http://test.org/bob"
                                ["http://is/a" "http://class/Person"]
                                ["http://rdfs/label" (s "Bob Jones")]
                                ["http://date-of-birth/" #inst "1980-01-02"]])))

(defn make-concept-scheme []
  (-> (parse-csv "./test-data/glasgow-life-facilities.csv")
      (csv/drop-rows 1)

      ))
