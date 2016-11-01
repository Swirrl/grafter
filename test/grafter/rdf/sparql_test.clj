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
        (let [rewritten (#'grafter.rdf.sparql/rewrite-limit-and-offset-clauses sparql-file limitsoffsets)]
          (is (= rewritten processed-sparql-file)))

      {::sparql/limits {:myLimitVar 55
                        7 39}
       ::sparql/offsets {0 50}}

      {::sparql/limits [[:myLimitVar 55]
                        [7 39]]
       ::sparql/offsets [[0 50]]})))

(deftest query-test
  (let [r (repo/fixture-repo "./test/grafter/rdf/sparql-data.trig")
        total-quads (count (into #{} r))
        spog (partial query "./grafter/rdf/select-spog.sparql")]
    (testing "limits"
      (let [num-results (count (spog {:s (URI. "http://example.org/data/another-triple")
                                       ::sparql/limits {99999 2}} r))]
        (is (= 2 num-results))))

    (testing "offsets"
      (is (= 2 (count (spog {:s (URI. "http://example.org/data/another-triple")
                             ::sparql/offsets {0 1}}
                            r)))))))
