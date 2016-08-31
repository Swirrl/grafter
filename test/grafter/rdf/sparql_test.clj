(ns grafter.rdf.sparql-test
  (:require [grafter.rdf.sparql :refer :all :as sparql]
            [clojure.test :refer :all]
            [clojure.java.io :refer [resource]]
            [grafter.rdf.repository :as repo])
  (:import java.net.URI))

(deftest pre-process-limit-clauses-test
  (let [sparql-file (slurp (resource "./grafter/rdf/select-spog-unprocessed.sparql"))
        processed-sparql-file (slurp (resource "./grafter/rdf/select-spog-pre-processed.sparql"))]

    (are [limitsoffsets]
        (let [rewritten (rewrite-limit-and-offset-clauses sparql-file limitsoffsets)]
          (is (= rewritten processed-sparql-file)))

      {::sparql/limits {:myLimitVar 55
                        7 39}
       ::sparql/offsets {0 50}}

      {::sparql/limits [[:myLimitVar 55]
                        [7 39]]
       ::sparql/offsets [[0 50]]})))

(deftest query-test
  (let [r (repo/fixture-repo "./test/grafter/rdf/sparql-data.trig")
        spog (partial query "./grafter/rdf/select-spog-unprocessed.sparql")
        query-result (first (spog {:s (URI. "http://example.org/data/a-triple")
                                   ::sparql/limits {"myLimitVar" 349
                                                    1 2}} r))]
    (is (= query-result
           {:s (URI. "http://example.org/data/a-triple")
            :p (URI. "http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
            :o (URI. "http://example.org/data/Quad")
            :g (URI. "http://example.org/graph/more-quads")}))))
