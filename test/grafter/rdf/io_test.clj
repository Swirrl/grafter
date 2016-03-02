(ns grafter.rdf.io-test
  (:require [clojure.test :refer :all]
            [grafter.rdf.protocols :refer [->Quad]]
            [grafter.rdf :refer [add statements]]
            [grafter.rdf.templater :refer [graph]]
            [grafter.rdf.io :refer :all]
            [grafter.rdf.formats :refer :all]
            [grafter.url :refer :all])
  (:import [org.openrdf.model.impl LiteralImpl URIImpl ContextStatementImpl]))

(deftest mimetype->rdf-format-test
  (testing "mimetype->rdf-format"

    (is (= rdf-ntriples
           (mimetype->rdf-format "application/n-triples; charset=UTF-8"))
        "works with charset parameters")

    (is (= rdf-xml
           (mimetype->rdf-format "application/rdf+xml"))
        "works without charset parameters")

    (is (thrown? IllegalArgumentException
                 (mimetype->rdf-format nil))
        "throws on nil mime type")))

(deftest round-trip-integer-types-test
  (let [types [["http://www.w3.org/2001/XMLSchema#byte" Byte "10"]
               ["http://www.w3.org/2001/XMLSchema#short" Short "10"]
               ["http://www.w3.org/2001/XMLSchema#decimal" java.math.BigDecimal "10"]
               ["http://www.w3.org/2001/XMLSchema#double" Double "10.7"]
               ["http://www.w3.org/2001/XMLSchema#float" Float "10.6"]
               ["http://www.w3.org/2001/XMLSchema#integer" BigInteger "10"]
               ["http://www.w3.org/2001/XMLSchema#int" Integer "10"]]]

    (doseq [[xsd type number] types]
      (is (= number (.stringValue (->sesame-rdf-type (sesame-rdf-type->type (LiteralImpl. number (URIImpl. xsd))))))))))

(deftest round-trip-literal-test
  (let [lit (literal "10" "http://www.w3.org/2001/XMLSchema#byte")]
    (is (= (byte 10) (sesame-rdf-type->type (->sesame-rdf-type lit))))))

(deftest round-trip-quad-test
  (let [quad (->Quad "http://example.org/test/subject"
                     "http://example.org/test/predicate"
                     "http://example.org/test/object"
                     "http://example.org/test/graph")]
    (is (= quad
           (sesame-statement->IStatement (IStatement->sesame-statement quad)))))

  (testing "with nil graph"
    (let [quad (->Quad "http://example.org/test/subject"
                       "http://example.org/test/predicate"
                       "http://example.org/test/object"
                       nil)]
      (is (= quad
             (sesame-statement->IStatement (IStatement->sesame-statement quad)))))))

(deftest round-trip-quad-serialize-deserialize-test
  (let [quad (graph "http://example.org/test/graph"
                    ["http://test/subj" ["http://test/pred" "http://test/obj"]])
        string-wtr (java.io.StringWriter.)
        serializer (rdf-serializer string-wtr :format rdf-nquads)]
    (add serializer quad)
    (is (= quad
           (statements (java.io.StringReader. (.toString string-wtr)) :format rdf-nquads)))))


(deftest IStatement->sesame-statement-test
  (testing "IStatement->sesame-statement"
    (is (= (IStatement->sesame-statement (->Quad "http://foo.com/" "http://bar.com/" "http://baz.com/" "http://blah.com/"))
           (ContextStatementImpl. (URIImpl. "http://foo.com/") (URIImpl. "http://bar.com/") (URIImpl. "http://baz.com/") (URIImpl. "http://blah.com/"))))

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

(deftest literal-and-literal-datatype->type-test
  (are [clj-val uri klass]
      (let [ret-val (literal-datatype->type (literal clj-val uri))]
        (is (= clj-val ret-val))
        (is (= klass (class clj-val))))

    true           "http://www.w3.org/2001/XMLSchema#boolean" Boolean
    (byte 10)      "http://www.w3.org/2001/XMLSchema#byte" Byte
    (short 12)     "http://www.w3.org/2001/XMLSchema#short" Short
    (bigint 9)     "http://www.w3.org/2001/XMLSchema#decimal" clojure.lang.BigInt
    (double 33.33) "http://www.w3.org/2001/XMLSchema#double" Double
    (float 23.8)   "http://www.w3.org/2001/XMLSchema#float" Float

    ;; Yes this is correct according to the XSD spec. #integer is
    ;; unbounded whereas #int is bounded
    (bigint 3)     "http://www.w3.org/2001/XMLSchema#integer" clojure.lang.BigInt
    (int 42)       "http://www.w3.org/2001/XMLSchema#int" Integer
    (s "hello")        "http://www.w3.org/2001/XMLSchema#string" grafter.rdf.protocols.RDFString
    (s "hi")          "http://www.w3.org/TR/xmlschema11-2/#string" grafter.rdf.protocols.RDFString
    )
  )
