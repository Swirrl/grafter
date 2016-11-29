(ns grafter.rdf.ontologies.nmo
  (:use [grafter.rdf.ontologies.util]))

(def nmo (prefixer "http://www.semanticdesktop.org/ontologies/2007/03/22/nmo#"))

(def nmo:emailCc (nmo "emailCc"))

(def nmo:emailTo (nmo "emailTo"))

(def nmo:emailFrom (nmo "emailFrom"))

(def nmo:messageSubject (nmo "messageSubject"))

(def nmo:messageID (nmo "messageID"))

(def nmo:sentDate (nmo "sentDate"))

(def nmo:hasAttachment (nmo "hasAttachment"))

