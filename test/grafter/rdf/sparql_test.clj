(ns grafter.rdf.sparql-test
  (:require [grafter.rdf.sparql :refer :all]
            [clojure.test :refer :all]
            [clojure.java.io :refer [resource]]
            [grafter.rdf.repository :refer [repo]])
  (:import java.net.URI))

(deftest pre-process-limit-clauses-test
  (let [sparql-file (slurp (resource "./grafter/rdf/select-spog-unprocessed.sparql"))
        processed-sparql-file (slurp (resource "./grafter/rdf/select-spog-pre-processed.sparql"))]
    (is (= (#'grafter.rdf.sparql/pre-process-limit-clauses sparql-file
                                      {:myLimitVar 55
                                       7 39})
           processed-sparql-file))))

(deftest query-test
  (let [r (repo "./test/grafter/rdf/sparql-data.trig")
        spog (partial query "./grafter/rdf/select-spog-unprocessed.sparql")
        query-result (first (spog r {:s (URI. "http://example.org/data/a-triple")
                                     :limits {"myLimitVar" 349
                                              1 2}}))]
    (is (= query-result
           {:s (URI. "http://example.org/data/a-triple")
            :p (URI. "http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
            :o (URI. "http://example.org/data/Quad")
            :g (URI. "http://example.org/graph/more-quads")}))))
