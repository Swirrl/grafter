(ns grafter.rdf.formats-test
  (:require [grafter.rdf.formats :as fmt]
            [clojure.test :refer :all]
            [clojure.java.io :as io])
  (:import [org.openrdf.rio RDFFormat]
           [java.net URI URL]))

(deftest mimetype->rdf-format-test
  (testing "mimetype->rdf-format"

    (is (= fmt/rdf-ntriples
           (fmt/mimetype->rdf-format "application/n-triples; charset=UTF-8"))
        "works with charset parameters")

    (is (= fmt/rdf-xml
           (fmt/mimetype->rdf-format "application/rdf+xml"))
        "works without charset parameters")

    (is (nil? (fmt/mimetype->rdf-format nil))
        "returns nil on nil mime type")))


(deftest ->rdf-format-test
  (is (= (fmt/->rdf-format :nt)
         (fmt/->rdf-format "nt")
         (fmt/->rdf-format "application/n-triples")
         (fmt/->rdf-format "text/plain")
         (fmt/->rdf-format (io/file "/tmp/foo.nt"))
         (fmt/->rdf-format (URI. "http://foo.bar.com/tmp/foo.nt"))
         (fmt/->rdf-format (URL. "http://foo.bar.com/tmp/foo.nt"))
         (fmt/->rdf-format (URI. "http://foo.bar.com/tmp/foo.nt?query=string&is=ignored"))
         fmt/rdf-ntriples
         RDFFormat/NTRIPLES))

  (is (nil? (fmt/->rdf-format "/tmp/foo.nt"))
      "File path strings are not parsed for formats.  To do this provide you need to provide a File object.")

  (is (= (fmt/->rdf-format :ttl)
         (fmt/->rdf-format "ttl")
         (fmt/->rdf-format "text/turtle")
         fmt/rdf-turtle
         RDFFormat/TURTLE))

  (is (= (fmt/->rdf-format :trig)
         (fmt/->rdf-format "trig")
         (fmt/->rdf-format "application/trig")
         (fmt/->rdf-format "application/x-trig")
         fmt/rdf-trig
         RDFFormat/TRIG))

  (is (= (fmt/->rdf-format :nq)
         (fmt/->rdf-format "nq")
         (fmt/->rdf-format "application/n-quads")
         (fmt/->rdf-format "text/x-nquads")
         (fmt/->rdf-format "text/nquads")
         fmt/rdf-nquads
         RDFFormat/NQUADS))

  (is (= (fmt/->rdf-format :rdf)
         (fmt/->rdf-format "rdf")
         (fmt/->rdf-format :rdfs)
         (fmt/->rdf-format "owl")
         (fmt/->rdf-format :owl)
         (fmt/->rdf-format "application/rdf+xml")
         (fmt/->rdf-format "application/xml")
         fmt/rdf-xml
         RDFFormat/RDFXML)))
