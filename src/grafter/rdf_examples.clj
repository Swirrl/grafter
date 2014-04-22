(ns grafter.rdf-examples
  (:use [grafter.rdf]
        [grafter.rdf.sesame]
        [grafter.csv :as csv]
        [grafter.rdf.ontologies.rdf]
        [grafter.rdf.ontologies.void]
        [grafter.rdf.ontologies.dcterms]
        [grafter.rdf.ontologies.pmd])
  (:require [grafter.rdf.protocols :as pr]))

(def glasgow (prefixer "http://linked.glasgow.gov.uk/def/"))
(def urban (prefixer "http://linked.glasgow.gov.uk/def/urban-assets/"))

(defonce my-repo (-> "./tmp/grafter-sesame-store" native-store repo))

(defn urban-assets-ontology [ont-uri]
  (triplify [ont-uri
             [rdf:a rdfs:Class]
             [rdfs:label (s "Urban Assets Ontology" :en)]]

            [(urban "Asset")
             [rdf:a rdfs:Class]
             [rdfs:label (s "Urban Asset")]
             [(rdfs "isDefinedBy") ont-uri]]

            [(glasgow "refAsset")
             [rdf:a (rdf "Property")]
             [rdf:a (qb "DimensionProperty")]
             [rdfs:label (s "Reference Asset" :en)]
             [(rdfs "range") (urban "Asset")]
             [(rdfs "isDefinedBy") ont-uri]]

            [(glasgow "numAssets")
             [rdf:a (rdf "Property")]
             [rdf:a (qb "MeasureProperty")]
             [rdfs:label (s "Number of Assets" :en)]
             [(rdfs "subPropertyOf") (sdmxmeasure "obsValue")]
             [(rdfs "isDefinedBy") ont-uri]]))

(defn internal-ontology-metadata [ontology-uri]
  (let [now (java.util.Date.)]
    (triplify [ontology-uri
               [pmd:contactEmail "mailto:hello@glasgow.gov.uk"]
               [dcterms:title (s "Urban Assets Ontology" :en)]
               [dcterms:issued now]
               [dcterms:modified now]])))

(defn import-ontology []
  (let [ont-uri (urban "ontology")
        ont-graph "http://linked.glasgow.gov.uk/graph/vocab/urban-assets/ontology"
        data (concat (urban-assets-ontology ont-uri) (internal-ontology-metadata ont-uri))]

    (-> my-repo
        (pr/add ont-graph data))))

(defn make-concept-scheme []
  (-> (parse-csv "./test-data/glasgow-life-facilities.csv")
      (csv/drop-rows 1)

      ))
