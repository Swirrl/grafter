(ns grafter.rdf.io-test
  (:require [clojure.test :refer :all]
            [grafter.rdf.protocols :refer [->Quad]]
            [grafter.rdf :refer [add statements]]
            [grafter.rdf.templater :refer [graph]]
            [grafter.rdf.io :refer :all]
            [grafter.url :refer :all]
            [grafter.rdf.protocols :as pr]
            [schema.core :as s]
            [grafter.rdf.formats :as fmt]
            [clojure.java.io :as io])
  (:import [org.openrdf.model.impl LiteralImpl URIImpl ContextStatementImpl]
           [java.net URI]))

;; NOTE this function is deprecated in favour of the one in formats,
;; but unlike the variation in formats it raises an exception if the
;; supplied argument is nil.
(deftest mimetype->rdf-format-test
  (testing "mimetype->rdf-format"

    (is (= fmt/rdf-ntriples
           (mimetype->rdf-format "application/n-triples; charset=UTF-8"))
        "works with charset parameters")

    (is (= fmt/rdf-xml
           (mimetype->rdf-format "application/rdf+xml"))
        "works without charset parameters")

    (is (thrown? IllegalArgumentException
                 (mimetype->rdf-format nil))
        "throws on nil mime type")))

(deftest round-trip-numeric-types-test
  (are [xsd type number]
      (is (= number (pr/raw-value (->sesame-rdf-type (sesame-rdf-type->type (LiteralImpl. number (URIImpl. xsd)))))))

    "http://www.w3.org/2001/XMLSchema#byte" Byte "10"
    "http://www.w3.org/2001/XMLSchema#short" Short "10"
    "http://www.w3.org/2001/XMLSchema#decimal" java.math.BigDecimal "10"
    "http://www.w3.org/2001/XMLSchema#double" Double "10.7"
    "http://www.w3.org/2001/XMLSchema#float" Float "10.6"
    "http://www.w3.org/2001/XMLSchema#integer" BigInteger "10"
    "http://www.w3.org/2001/XMLSchema#int" Integer "10"))

(deftest literal-and-literal-datatype->type-test
  (are [clj-val uri klass]
      (let [ret-val (literal-datatype->type (literal clj-val uri))]
        (is (= clj-val ret-val))
        (is (= klass (class clj-val))))

    true           "http://www.w3.org/2001/XMLSchema#boolean" Boolean
    (byte 10)      "http://www.w3.org/2001/XMLSchema#byte" Byte
    (short 12)     "http://www.w3.org/2001/XMLSchema#short" Short
    (bigdec 9)     "http://www.w3.org/2001/XMLSchema#decimal" java.math.BigDecimal
    (double 33.33) "http://www.w3.org/2001/XMLSchema#double" Double
    (float 23.8)   "http://www.w3.org/2001/XMLSchema#float" Float
    10             "http://www.w3.org/2001/XMLSchema#long" Long

    ;; Yes this is correct according to the XSD spec. #integer is
    ;; unbounded whereas #int is bounded
    (bigint 3)     "http://www.w3.org/2001/XMLSchema#integer" clojure.lang.BigInt
    (int 42)       "http://www.w3.org/2001/XMLSchema#int" Integer
    "hello"        "http://www.w3.org/2001/XMLSchema#string" String))

(deftest literal-test
  (is (instance? LiteralImpl (->sesame-rdf-type (literal "2014-01-01" (java.net.URI. "http://www.w3.org/2001/XMLSchema#date"))))))

(deftest language-string-test
  (let [bonsoir (language "Bonsoir Mademoiselle" :fr)]
    (is (= bonsoir (literal-datatype->type bonsoir)))
    (is (= bonsoir (sesame-rdf-type->type (->sesame-rdf-type bonsoir))))))

(deftest round-trip-quad-test
  (let [quad (->Quad (->java-uri "http://example.org/test/subject")
                     (->java-uri "http://example.org/test/predicate")
                     (->java-uri "http://example.org/test/object")
                     (->java-uri "http://example.org/test/graph"))]
    (is (= quad
           (sesame-statement->IStatement (IStatement->sesame-statement quad)))))

  (testing "with nil graph"
    (let [quad (->Quad (->java-uri "http://example.org/test/subject")
                       (->java-uri "http://example.org/test/predicate")
                       (->java-uri "http://example.org/test/object")
                       nil)]
      (is (= quad
             (sesame-statement->IStatement (IStatement->sesame-statement quad)))))))

(deftest round-trip-quad-serialize-deserialize-test
  (let [quad (graph (->java-uri "http://example.org/test/graph")
                    [(->java-uri "http://test/subj") [(->java-uri "http://test/pred") (->java-uri "http://test/obj")]])
        string-wtr (java.io.StringWriter.)
        serializer (rdf-serializer string-wtr :format :nq)]
    (add serializer quad)

    (let [output-str (str string-wtr)]
      (with-open [rdr (java.io.StringReader. output-str)]
        (is (= quad
               (statements rdr :format :nq)))))))


(deftest IStatement->sesame-statement-test
  (testing "IStatement->sesame-statement"
    (is (= (IStatement->sesame-statement (->Quad (->java-uri "http://foo.com/") (->java-uri "http://bar.com/") "a string" (->java-uri "http://blah.com/")))
           (ContextStatementImpl. (URIImpl. "http://foo.com/") (URIImpl. "http://bar.com/") (LiteralImpl. "a string") (URIImpl. "http://blah.com/"))))

    (testing "Raising Exceptions"
      (let [broken-quad (with-meta (->Quad nil "http://bar.com/" "http://baz.com/" "http://blah.com/") {:foo :bar})
            ex (ex-data (is (thrown? clojure.lang.ExceptionInfo
                                     (IStatement->sesame-statement broken-quad))))]

        (is (= (->Quad
                nil
                "http://bar.com/"
                "http://baz.com/"
                "http://blah.com/") (:quad ex))
            "Adds the statement itself to the exception data")

        (is (= {:foo :bar} (:quad-meta ex))
            "Metadata from quads is reported in exception data")))))

(deftest to-grafter-url-protocol-test
  (testing "extends RDF Model URI"
    (let [uri (URIImpl. "http://www.tokyo-3.com:777/ayanami?geofront=retracted")
          grafter-url (->grafter-url uri)]
      (are [expected actual] (= expected actual)
                             777 (port grafter-url)
                             "www.tokyo-3.com" (host grafter-url)
                             "http" (scheme grafter-url)
                             ["ayanami"] (path-segments grafter-url)))))

(def BlankObjectNode {:s URI :p URI :o s/Keyword :c (s/eq nil)})

(def BlankSubjectNode {:s s/Keyword :p URI :o URI :c (s/eq nil) })


(deftest blank-nodes-load-test
  (let [[btriple1 btriple2] (statements (io/resource "grafter/rdf/bnodes.nt"))]
    (is (s/validate BlankObjectNode btriple1))
    (is (s/validate BlankSubjectNode btriple2))))
