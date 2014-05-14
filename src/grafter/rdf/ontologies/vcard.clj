(ns grafter.rdf.ontologies.vcard
  (:use [grafter.rdf.ontologies.util]))

(def vcard (prefixer "http://www.w3.org/2006/vcard/ns"))

(def vcard:Address (vcard "Address"))

(def vcard:hasAddress (vcard "hasAddress"))

(def vcard:hasUrl (vcard "hasUrl"))

(def vcard:street-address (vcard "street-address"))

(def vcard:postal-code (vcard "postal-code"))

(def vcard:locality (vcard "locality"))

(def vcard:country-name (vcard "country-name"))
