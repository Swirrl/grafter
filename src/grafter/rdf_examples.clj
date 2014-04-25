(ns grafter.rdf-examples
  (:use [grafter.rdf]
        [grafter.rdf.sesame]
        [grafter.csv :as csv]
        [grafter.rdf.ontologies.rdf]
        [grafter.rdf.ontologies.void]
        [grafter.rdf.ontologies.dcterms]
        [grafter.rdf.ontologies.vcard]
        [grafter.rdf.ontologies.pmd]
        [grafter.rdf.ontologies.qb]
        [grafter.rdf.ontologies.sdmxmeasure]
        [grafter.parse]
        [clojure.algo.monads])
  (:require [grafter.rdf.protocols :as pr]
            [clojure.string :as st]))

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

(def uriify-facility {"Museums" (urban "Museum")
                      "Arts" (urban "ArtsCentre")
                      "Community Facility" (urban "CommunityFacility")
                      "Libraries" (urban "Library")
                      "Music" (urban "MusicVenue")
                      "Sport Centres" (urban "SportsCentre")})

(defn make-life-facilities []
  ;;Facility description | Facillity name | Monthly attendence |Month | Year | Address | Town | Postcode | Website

  (with-monad blank-m
    (let [rdfstr        (lift-1 (fn [str] (s str :en)))
          replace-comma (lift-1 (replacer "," ""))
          trim          (lift-1 clojure.string/trim)
          parse-integer (m-chain [trim replace-comma parse-int])
          convert-month (m-chain [trim
                                 (lift-1 clojure.string/lower-case)
                                 (lift-1 {"january" 1 "jan" 1 "1" 1
                                          "february" 2 "feb" 2 "2" 2
                                          "march" 3 "mar" 3 "3" 3
                                          "april" 4 "apr" 4 "4" 4
                                          "may" 5 "5" 5
                                          "june" 6 "jun" 6 "6"  6
                                          "july" 7 "jul" 7 "7"  7
                                          "august" 8 "aug" 8 "8" 8
                                          "september" 9 "sep" 9 "sept" 9 "9"  9
                                          "october" 10 "oct" 10 "10" 10
                                          "november" 11 "nov" 11 "11" 11
                                          "december" 12 "dec" 12 "12" 12
                                          })])
          convert-year  (m-chain [trim parse-int date-time])
          address-line  (m-chain [trim rdfstr])
          city          (m-chain [trim rdfstr])
          post-code     (m-chain [trim rdfstr])
          uriify-pcode  (m-chain [trim
                                  (lift-1 (replacer " " ""))
                                  (lift-1 clojure.string/upper-case)
                                  (lift-1 (prefixer "http://data.ordnancesurvey.co.uk/id/postcodeunit/"))])
          url           (lift-1 #(java.net.URL. %))]

      (let [processed-rows
            (-> (parse-csv "./test-data/glasgow-life-facilities.csv")
                (drop-rows 1)
                (swap {3 4})
                (mapc [uriify-facility _ parse-integer parse-integer convert-month address-line city post-code url])
                (derive-column uriify-pcode 6)
                (fuse date-time 3 4))]

        (mapcat (fn [[facility-uri name attendance date street-address city postcode website postcode-uri :as row]]
                  (triplify [facility-uri
                             [vcard:hasAddress [[rdf:a vcard:Address]
                                                [vcard:street-address street-address]
                                                [vcard:locality city]
                                                [vcard:country-name (rdfstr "Scotland")]
                                                [vcard:postal-code postcode-uri]
                                                ["http://data.ordnancesurvey.co.uk/ontology/postcode/postcode" postcode-uri]]]]))
                processed-rows)))))

(defn load [triple-seq]
  (pr/add my-repo  triple-seq))

(defn import-life-facilities []
  (-> (make-life-facilities)
      load))
