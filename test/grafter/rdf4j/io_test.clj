(ns grafter.rdf4j.io-test
  (:require [clojure.test :refer :all]
            [grafter.rdf4j.io :as sut]
            [grafter.rdf4j :refer [statements]]
            [grafter.core :refer [->Quad add] :as core]
            [grafter.rdf4j.templater :refer [graph]]
            [grafter.url :as url]
            [grafter.rdf4j.formats :as fmt]
            [clojure.java.io :as io])
  (:import [org.eclipse.rdf4j.model.impl LiteralImpl URIImpl ContextStatementImpl]
           [java.net URI]
           [java.sql Time]
           [java.util Date]))


(deftest round-trip-numeric-types-test
  (are [xsd type number]
      (is (= number (core/raw-value (sut/->backend-type (core/->grafter-type (LiteralImpl. number (URIImpl. xsd)))))))

    "http://www.w3.org/2001/XMLSchema#byte" Byte "10"
    "http://www.w3.org/2001/XMLSchema#short" Short "10"
    "http://www.w3.org/2001/XMLSchema#decimal" java.math.BigDecimal "10"
    "http://www.w3.org/2001/XMLSchema#double" Double "10.7"
    "http://www.w3.org/2001/XMLSchema#float" Float "10.6"
    "http://www.w3.org/2001/XMLSchema#integer" BigInteger "10"
    "http://www.w3.org/2001/XMLSchema#int" Integer "10"))

(deftest backend-literal->grafter-type-test
  (are [clj-val uri klass]
      (let [ret-val (sut/backend-literal->grafter-type (sut/->backend-type clj-val))]
        (is (= clj-val ret-val))
        (is (= klass (class clj-val))))

    true           "http://www.w3.org/2001/XMLSchema#boolean" Boolean
    (byte 10)      "http://www.w3.org/2001/XMLSchema#byte" Byte
    (short 12)     "http://www.w3.org/2001/XMLSchema#short" Short
    (bigdec 9)     "http://www.w3.org/2001/XMLSchema#decimal" java.math.BigDecimal
    (double 33.33) "http://www.w3.org/2001/XMLSchema#double" Double
    (float 23.8)   "http://www.w3.org/2001/XMLSchema#float" Float
    10             "http://www.w3.org/2001/XMLSchema#long" Long

    #inst "2017-10-20T22:26:28.195-00:00" "http://www.w3.org/2001/XMLSchema#dateTime" java.util.Date

    ;; Yes this is correct according to the XSD spec. #integer is
    ;; unbounded whereas #int is bounded
    (bigint 3)     "http://www.w3.org/2001/XMLSchema#integer" clojure.lang.BigInt
    (int 42)       "http://www.w3.org/2001/XMLSchema#int" Integer
    "hello"        "http://www.w3.org/2001/XMLSchema#string" String
    (Time. (.getTime #inst "2017-11-20T10:38:22.143-00:00")) "http://www.w3.org/2001/XMLSchema#dateTime" Time
    #inst "2017-01-01" "http://www.w3.org/2001/XMLSchema#date" Date))


(deftest language-string-test
  (let [bonsoir (core/language "Bonsoir Mademoiselle" :fr)]
    (is (= bonsoir (sut/backend-literal->grafter-type bonsoir)))
    (is (= bonsoir (core/->grafter-type (sut/->backend-type bonsoir))))))

(deftest literal-test
  (is (instance? LiteralImpl (sut/->backend-type (core/literal "2014-01-01" (java.net.URI. "http://www.w3.org/2001/XMLSchema#date"))))))

(deftest round-trip-quad-test
  (testing "round trips"
    (testing "quad"
      (let [quad (->Quad (url/->java-uri "http://example.org/test/subject")
                         (url/->java-uri "http://example.org/test/predicate")
                         (url/->java-uri "http://example.org/test/object")
                         (url/->java-uri "http://example.org/test/graph"))]
        (is (= quad
               (sut/backend-quad->grafter-quad (sut/quad->backend-quad quad))))))

    (testing "quad with bnode"
      (let [quad (->Quad (url/->java-uri "http://example.org/test/subject")
                         (url/->java-uri "http://example.org/test/predicate")
                         :bnode-1
                         (url/->java-uri "http://example.org/test/graph"))]
        (is (= quad
               (sut/backend-quad->grafter-quad (sut/quad->backend-quad quad))))))

    (testing "triple (nil graph)"
      (let [quad (->Quad (url/->java-uri "http://example.org/test/subject")
                         (url/->java-uri "http://example.org/test/predicate")
                         (url/->java-uri "http://example.org/test/object")
                         nil)]
        (is (= quad
               (sut/backend-quad->grafter-quad (sut/quad->backend-quad quad))))))))

(deftest round-trip-quad-serialize-deserialize-test
  (let [quad (graph (url/->java-uri "http://example.org/test/graph")
                    [(url/->java-uri "http://test/subj") [(url/->java-uri "http://test/pred") (url/->java-uri "http://test/obj")]])
        string-wtr (java.io.StringWriter.)
        serializer (sut/rdf-writer string-wtr :format :nq)]
    (add serializer quad)

    (let [output-str (str string-wtr)]
      (with-open [rdr (java.io.StringReader. output-str)]
        (is (= quad
               (statements rdr :format :nq)))))))

(deftest binary-rdf-test
  (testing "round trip quads via binary RDF"
    (let [baos (java.io.ByteArrayOutputStream. 8192)
          quads (graph (url/->java-uri "http://example.org/test/graph")
                       [(url/->java-uri "http://test/subj") [(url/->java-uri "http://test/pred") (url/->java-uri "http://test/obj")]])]

      (add (sut/rdf-writer baos :format :brf) quads)

      (let [bais (java.io.ByteArrayInputStream. (.toByteArray baos))]
        (is (= (statements bais :format :brf)
               quads))))))

(deftest quad->backend-quad-test
  (testing "IStatement->sesame-statement"
    (is (= (sut/quad->backend-quad (->Quad (url/->java-uri "http://foo.com/") (url/->java-uri "http://bar.com/") "a string" (url/->java-uri "http://blah.com/")))
           (ContextStatementImpl. (URIImpl. "http://foo.com/") (URIImpl. "http://bar.com/") (LiteralImpl. "a string") (URIImpl. "http://blah.com/"))))

    (testing "Raising Exceptions"
      (let [broken-quad (with-meta (->Quad nil "http://bar.com/" "http://baz.com/" "http://blah.com/") {:foo :bar})
            ex (ex-data (is (thrown? clojure.lang.ExceptionInfo
                                     (sut/quad->backend-quad broken-quad))))]

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
          grafter-url (url/->grafter-url uri)]
      (are [expected actual] (= expected actual)
                             777 (url/port grafter-url)
                             "www.tokyo-3.com" (url/host grafter-url)
                             "http" (url/scheme grafter-url)
                             ["ayanami"] (url/path-segments grafter-url)))))

(deftest blank-nodes-load-test
  (testing "Blank nodes are keywords"
    (let [[[s1 p1 o1] [s2 p2 o2]] (statements (io/resource "grafter/rdf/bnodes.nt"))]
      (is (keyword? o1))
      (is (keyword? s2)))))
