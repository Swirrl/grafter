(ns grafter.rdf.io-test
  (:require [clojure.test :refer :all]
            [grafter.rdf.io :refer :all]
            [grafter.rdf.formats :refer :all])
  (:import [org.openrdf.model.impl LiteralImpl URIImpl]))

(deftest mimetype->rdf-format-test
  (testing "mimetype->rdf-format"

    (is (= rdf-ntriples
           (mimetype->rdf-format "application/n-triples; charset=UTF-8"))
        "works with charset parameters")

    (is (= rdf-xml
           (mimetype->rdf-format "application/rdf+xml"))
        "works without charset parameters")))

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
