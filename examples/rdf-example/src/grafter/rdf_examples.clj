(ns grafter.rdf-examples
  (:use [grafter.rdf]
        [grafter.rdf.validation]
        [grafter.rdf.sesame]
        [grafter.csv :as csv]
        [grafter.rdf.ontologies.rdf]
        [grafter.rdf.ontologies.void]
        [grafter.rdf.ontologies.dcterms]
        [grafter.rdf.ontologies.vcard]
        [grafter.rdf.ontologies.pmd]
        [grafter.rdf.ontologies.qb]
        [grafter.rdf.ontologies.os]
        [grafter.rdf.ontologies.sdmx-measure]
        [grafter.parse]
        [grafter.js]
        [clojure.algo.monads])
  (:require [grafter.rdf.protocols :as pr]
            [clojure.string :as st]))


(def base-uri (prefixer "http://linked.glasgow.gov.uk"))
(def base-graph (prefixer (base-uri "/graph/")))

(def glasgow (prefixer "http://linked.glasgow.gov.uk/def/"))
(def urban (prefixer "http://linked.glasgow.gov.uk/def/urban-assets/"))
(def ont-graph "http://linked.glasgow.gov.uk/graph/vocab/urban-assets/ontology")

(def attendance (prefixer "http://linked.glasgow.gov.uk/data/facility_attendance"))
(def urban:ontology (urban "ontology"))
(def sd (prefixer "http://data.opendatascotland.org/def/statistical-dimensions/"))

(defonce my-repo (-> "./tmp/grafter-sesame-store" native-store repo))
;;(defonce my-repo (repo (memory-store)))

(def uriify-facility {"Museums" (urban "Museum")
                      "Arts" (urban "ArtsCentre")
                      "Community Facility" (urban "CommunityFacility")
                      "Libraries" (urban "Library")
                      "Music" (urban "MusicVenue")
                      "Sport Centres" (urban "SportsCentre")})

(defn date-slug [date]
  (str (.getYear date) "-" (.getMonthOfYear date) "/"))

(def slugify-facility
  (js-fn "function(name) {
              var lower = name.toLowerCase();
              return lower.replace(/\\ /g, '-');
         }"))

(defn make-life-facilities []
  (with-monad blank-m
    (let [rdfstr                    (lift-1 (fn [str] (s str :en)))
          replace-comma             (lift-1 (replacer "," ""))
          trim                      (lift-1 clojure.string/trim)
          parse-attendance          (with-monad identity-m (m-chain [(lift-1 (mapper {"" "0"}))
                                                                     (lift-1 (replacer "," ""))
                                                                     trim
                                                                     parse-int]))
          parse-year                (m-chain [trim replace-comma parse-int])
          convert-month             (m-chain [trim
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
          convert-year              (m-chain [trim parse-int date-time])
          address-line              (m-chain [trim rdfstr])
          city                      (m-chain [trim rdfstr])
          post-code                 (m-chain [trim rdfstr])
          uriify-pcode              (m-chain [trim
                                              (lift-1 (replacer " " ""))
                                              (lift-1 clojure.string/upper-case)
                                              (lift-1 (prefixer "http://data.ordnancesurvey.co.uk/id/postcodeunit/"))])
          url                       (lift-1 #(java.net.URL. %))

          prefix-monthly-attendance (m-chain [(lift-1 date-slug)
                                              (lift-1 (prefixer "/community-facility/"))])
          prefix-facility           (prefixer "http://linked.glasgow.gov.uk/data/facility_attendance")]

      (let [processed-rows
            (-> (parse-csv "./test-data/glasgow-life-facilities.csv")
                (drop-rows 1)
                (swap {3 4})
                (mapc [uriify-facility _ parse-attendance parse-year convert-month address-line city post-code url])
                (derive-column uriify-pcode 7)
                (fuse date-time 3 4)
                (derive-column prefix-monthly-attendance 3)
                (derive-column slugify-facility 1)
                (fuse str 9 10)
                (derive-column prefix-facility 9))]

        ((graphify [facility-uri name attendance date street-address city postcode website postcode-uri
                    _ observation-uri]

                   (graph (base-graph "glasgow-life-facilities")
                          [facility-uri
                           [vcard:hasAddress [[rdf:a vcard:Address]
                                              [vcard:street-address street-address]
                                              [vcard:locality city]
                                              [vcard:country-name (rdfstr "Scotland")]
                                              [vcard:postal-code postcode-uri]
                                              [os:postcode postcode-uri]]]])

                   (graph (base-graph "glasgow-life-attendances")
                          [observation-uri
                           [(glasgow "refFacility") facility-uri]
                           [(glasgow "numAttendees") attendance]
                           [qb:dataSet "http://linked.glasgow.gov.uk/data/facility_attendance"]
                           [(sd "refPeriod") "http://reference.data.gov.uk/id/month/2013-09"]
                           [rdf:a qb:Observation]]))

         processed-rows) ;processed-rows
        ))))

(defn urban-assets-ontology [ont-uri]
  (graph "http://linked.glasgow.gov.uk/graph/vocab/urban-assets/ontology"
         [ont-uri
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
          [(rdfs "subPropertyOf") (sdmx-measure "obsValue")]
          [(rdfs "isDefinedBy") ont-uri]]))

(defn internal-ontology-metadata [ontology-uri date]
  (graph "http://linked.glasgow.gov.uk/graph/vocab/urban-assets/ontology/metadata"
         [ontology-uri
          [pmd:contactEmail "mailto:hello@glasgow.gov.uk"]
          [dcterms:title (s "Urban Assets Ontology" :en)]
          [dcterms:issued date]
          [dcterms:modified date]]))

(defn filter-triples [triples]
  (filter #(not (and (#{vcard:postal-code os:postcode} (pr/predicate %1))
                     (blank? (pr/object %1)))) triples))

(defn import-life-facilities [quads-seq]
  (let [now (java.util.Date.)]
    (->> quads-seq
         filter-triples
         (validate-triples (complement has-blank?))
         (load-triples my-repo))

    (->> (concat
          (dataset (str (base-uri "glasgow-life-facilities") "/data")
                   (str (base-graph "glasgow-life-facilities"))
                   now "Glasgow Life Facilities"
                   "Glasgow Life Facilities"
                   "List of Glasgow Life facilities"
                   "Sporting, cultural and social facilities in Glasgow."
                   "mailto:open@glasgow.gov.uk")

          (dataset (str (base-uri "glasgow-life-attendances"))
                   (str (base-graph "glasgow-life-attendances"))
                   now "Glasgow Life Attendances"
                   "Glasgow Life Attendances"
                   "Monthly Attendance figures for Glasgow Life Facilities"
                   "Monthly Attendances for Sporting, cultural and social facilities in Glasgow"
                   "mailto:open@glasgow.gov.uk")

          (urban-assets-ontology urban:ontology)
          (internal-ontology-metadata urban:ontology now))

         (load-triples my-repo))))
