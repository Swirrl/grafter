(ns grafter.rdf.formats-test
  (:require [grafter.rdf.formats :as fmt]
            [clojure.test :refer :all])
  (:import [org.openrdf.rio RDFFormat]))

(deftest coerce-format-test
  (is (= (fmt/coerce-format :nt)
         (fmt/coerce-format "nt")
         (fmt/coerce-format "application/n-triples")
         (fmt/coerce-format "text/plain")
         fmt/rdf-ntriples
         RDFFormat/NTRIPLES))

  (is (= (fmt/coerce-format :ttl)
         (fmt/coerce-format "ttl")
         (fmt/coerce-format "text/turtle")
         fmt/rdf-turtle
         RDFFormat/TURTLE))

  (is (= (fmt/coerce-format :trig)
         (fmt/coerce-format "trig")
         (fmt/coerce-format "application/trig")
         (fmt/coerce-format "application/x-trig")
         fmt/rdf-trig
         RDFFormat/TRIG))

  (is (= (fmt/coerce-format :nq)
         (fmt/coerce-format "nq")
         (fmt/coerce-format "application/n-quads")
         (fmt/coerce-format "text/x-nquads")
         (fmt/coerce-format "text/nquads")
         fmt/rdf-nquads
         RDFFormat/NQUADS))

  (is (= (fmt/coerce-format :rdf)
         (fmt/coerce-format "rdf")
         (fmt/coerce-format :rdfs)
         (fmt/coerce-format "owl")
         (fmt/coerce-format :owl)
         (fmt/coerce-format "application/rdf+xml")
         (fmt/coerce-format "application/xml")
         fmt/rdf-xml
         RDFFormat/RDFXML)))
