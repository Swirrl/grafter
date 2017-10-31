(ns grafter.rdf4j.io-test
  (:require [clojure.test :refer :all]
            [grafter.rdf.protocols :refer [->Quad]]
            [grafter.rdf :refer [add statements]]
            [grafter.rdf.templater :refer [graph]]
            [grafter.rdf4j.io :refer :all]
            [grafter.url :refer :all]
            [grafter.rdf.protocols :as pr]
            [grafter.rdf.formats :as fmt]
            [clojure.java.io :as io])
  (:import [org.eclipse.rdf4j.model.impl LiteralImpl URIImpl ContextStatementImpl]
           [java.net URI]))


(deftest round-trip-numeric-types-test
  (are [xsd type number]
      (is (= number (pr/raw-value (->backend-type (pr/->grafter-type (LiteralImpl. number (URIImpl. xsd)))))))

    "http://www.w3.org/2001/XMLSchema#byte" Byte "10"
    "http://www.w3.org/2001/XMLSchema#short" Short "10"
    "http://www.w3.org/2001/XMLSchema#decimal" java.math.BigDecimal "10"
    "http://www.w3.org/2001/XMLSchema#double" Double "10.7"
    "http://www.w3.org/2001/XMLSchema#float" Float "10.6"
    "http://www.w3.org/2001/XMLSchema#integer" BigInteger "10"
    "http://www.w3.org/2001/XMLSchema#int" Integer "10"))

(deftest backend-literal->grafter-type-test
  (are [clj-val uri klass]
      (let [ret-val (backend-literal->grafter-type (->backend-type clj-val))]
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
    "hello"        "http://www.w3.org/2001/XMLSchema#string" String))

(deftest language-string-test
  (let [bonsoir (pr/language "Bonsoir Mademoiselle" :fr)]
    (is (= bonsoir (backend-literal->grafter-type bonsoir)))
    (is (= bonsoir (pr/->grafter-type (->backend-type bonsoir))))))

(deftest literal-test
  (is (instance? LiteralImpl (->backend-type (pr/literal "2014-01-01" (java.net.URI. "http://www.w3.org/2001/XMLSchema#date"))))))

(deftest round-trip-quad-test
  (let [quad (->Quad (->java-uri "http://example.org/test/subject")
                     (->java-uri "http://example.org/test/predicate")
                     (->java-uri "http://example.org/test/object")
                     (->java-uri "http://example.org/test/graph"))]
    (is (= quad
           (backend-quad->grafter-quad (quad->backend-quad quad)))))

  (testing "with nil graph"
    (let [quad (->Quad (->java-uri "http://example.org/test/subject")
                       (->java-uri "http://example.org/test/predicate")
                       (->java-uri "http://example.org/test/object")
                       nil)]
      (is (= quad
             (backend-quad->grafter-quad (quad->backend-quad quad)))))))

(deftest round-trip-quad-serialize-deserialize-test
  (let [quad (graph (->java-uri "http://example.org/test/graph")
                    [(->java-uri "http://test/subj") [(->java-uri "http://test/pred") (->java-uri "http://test/obj")]])
        string-wtr (java.io.StringWriter.)
        serializer (rdf-writer string-wtr :format :nq)]
    (add serializer quad)

    (let [output-str (str string-wtr)]
      (with-open [rdr (java.io.StringReader. output-str)]
        (is (= quad
               (statements rdr :format :nq)))))))

(deftest binary-rdf-test
  (testing "round trip quads via binary RDF"
    (let [baos (java.io.ByteArrayOutputStream. 8192)
          quads (graph (->java-uri "http://example.org/test/graph")
                       [(->java-uri "http://test/subj") [(->java-uri "http://test/pred") (->java-uri "http://test/obj")]])]

      (add (rdf-writer baos :format :brf) quads)

      (let [bais (java.io.ByteArrayInputStream. (.toByteArray baos))]
        (is (= (statements bais :format :brf)
               quads))))))

(deftest quad->backend-quad-test
  (testing "IStatement->sesame-statement"
    (is (= (quad->backend-quad (->Quad (->java-uri "http://foo.com/") (->java-uri "http://bar.com/") "a string" (->java-uri "http://blah.com/")))
           (ContextStatementImpl. (URIImpl. "http://foo.com/") (URIImpl. "http://bar.com/") (LiteralImpl. "a string") (URIImpl. "http://blah.com/"))))

    (testing "Raising Exceptions"
      (let [broken-quad (with-meta (->Quad nil "http://bar.com/" "http://baz.com/" "http://blah.com/") {:foo :bar})
            ex (ex-data (is (thrown? clojure.lang.ExceptionInfo
                                     (quad->backend-quad broken-quad))))]

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

(deftest blank-nodes-load-test
  (testing "Blank nodes are keywords"
    (let [[[s1 p1 o1] [s2 p2 o2]] (statements (io/resource "grafter/rdf/bnodes.nt"))]
      (is (keyword? o1))
      (is (keyword? s2)))))
