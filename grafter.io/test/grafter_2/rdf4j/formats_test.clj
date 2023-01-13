(ns grafter-2.rdf4j.formats-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [grafter-2.rdf4j.formats :as fmt])
  (:import [java.net URI URL]
           org.eclipse.rdf4j.rio.RDFFormat))

(deftest mimetype->rdf-format-test
  (testing "mimetype->rdf-format"

    (is (= RDFFormat/NTRIPLES
           (fmt/mimetype->rdf-format "application/n-triples; charset=UTF-8"))
        "works with charset parameters")

    (is (= RDFFormat/RDFXML
           (fmt/mimetype->rdf-format "application/rdf+xml"))
        "works without charset parameters")

    (is (nil? (fmt/mimetype->rdf-format nil))
        "returns nil on nil mime type")))


(deftest ->rdf-format-test
  (is (= (fmt/->rdf-format :nt)
         (fmt/->rdf-format "nt")
         (fmt/->rdf-format "application/n-triples")
         (fmt/->rdf-format "text/plain")
         RDFFormat/NTRIPLES))
  (is (= (fmt/->rdf-format (URI. "http://foo.bar.com/tmp/foo.nt"))
         (fmt/->rdf-format (URL. "http://foo.bar.com/tmp/foo.nt"))
         (fmt/->rdf-format (io/file "/tmp/foo.nt"))
         (fmt/->rdf-format (URI. "http://foo.bar.com/tmp/foo.nt?query=string&is=ignored"))))

  (is (nil? (fmt/->rdf-format "/tmp/foo.nt"))
      "File path strings are not parsed for formats.  To do this provide you need to provide a File object.")

  (is (= (fmt/->rdf-format :ttl)
         (fmt/->rdf-format "ttl")
         (fmt/->rdf-format "text/turtle")
         RDFFormat/TURTLE))

  (is (= (fmt/->rdf-format :trig)
         (fmt/->rdf-format "trig")
         (fmt/->rdf-format "application/trig")
         (fmt/->rdf-format "application/x-trig")
         RDFFormat/TRIG))

  (is (= (fmt/->rdf-format :nq)
         (fmt/->rdf-format "nq")
         (fmt/->rdf-format "application/n-quads")
         (fmt/->rdf-format "text/x-nquads")
         (fmt/->rdf-format "text/nquads")
         RDFFormat/NQUADS))

  (is (= (fmt/->rdf-format :rdf)
         (fmt/->rdf-format "rdf")
         (fmt/->rdf-format :rdfs)
         (fmt/->rdf-format "owl")
         (fmt/->rdf-format :owl)
         (fmt/->rdf-format "application/rdf+xml")
         (fmt/->rdf-format "application/xml")
         RDFFormat/RDFXML)))
