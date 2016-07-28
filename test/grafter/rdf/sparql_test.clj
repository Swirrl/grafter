(ns grafter.rdf.sparql-test
  (:require [grafter.rdf.sparql :refer :all]
            [clojure.test :refer :all]
            [grafter.rdf.repository :refer [repo]])
  (:import  java.net.URI))


(deftest query-test
  (let [r (repo "./test/grafter/rdf/sparql-data.trig")
        spog (partial query "./grafter/rdf/select-spog.sparql")

        query-result (first (spog r {:s (URI. "http://example.org/data/a-triple")}))]

    (is (= query-result
           {:s (URI. "http://example.org/data/a-triple")
            :p (URI. "http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
            :o (URI. "http://example.org/data/Quad")
            :g (URI. "http://example.org/graph/more-quads")})
        )))
