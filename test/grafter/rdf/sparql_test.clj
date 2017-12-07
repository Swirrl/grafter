(ns grafter.rdf.sparql-test
  (:require [grafter.rdf.sparql :refer :all :as sparql]
            [grafter.rdf :as rdf]
            [clojure.test :refer :all]
            [clojure.java.io :refer [resource]]
            [grafter.rdf.repository :as repo]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [grafter.rdf.io :as rio]
            [grafter.rdf.protocols :as pr])
  (:import java.net.URI
           clojure.lang.ExceptionInfo))

(deftest pre-process-limit-clauses-test
  (let [sparql-file (slurp (resource "grafter/rdf/sparql/select-spog-unprocessed.sparql"))
        processed-sparql-file (slurp (resource "grafter/rdf/sparql/select-spog-pre-processed.sparql"))]

    (are [limitsoffsets]
        (let [rewritten (#'grafter.rdf.sparql/rewrite-limit-and-offset-clauses sparql-file limitsoffsets)]
          (is (= rewritten processed-sparql-file)))

      {::sparql/limits {:myLimitVar 55
                        7 39}
       ::sparql/offsets {0 50}}

      {::sparql/limits [[:myLimitVar 55]
                        [7 39]]
       ::sparql/offsets [[0 50]]})))

(defn same-query?
  "Helper to check string equality after all the whitespace has been
  removed."
  [q1 q2]
  (let [q1 (str/replace q1 #"\s" "")
        q2 (str/replace q2 #"\s" "")]
    (= q1 q2)))

(defn sparql-query [r]
  (slurp (io/resource r)))

(deftest pre-process-values-clauses-test
  (let [q1 (sparql-query "grafter/rdf/sparql/select-values-1.sparql")]
    (is (same-query?
         "SELECT * WHERE { # comment 1 VALUES ?s { <http://s1> <http://s2> } # comment 2 ?s ?p ?o . }"
         (#'sparql/rewrite-values-clauses q1 {:s [(URI. "http://s1") (URI. "http://s2")]}))))

  (let [q2 (sparql-query "grafter/rdf/sparql/select-values-2.sparql")]
    (is (same-query?
         (str "SELECT * WHERE { "
              "VALUES ?s { <http://s1> <http://s2> } "
              "VALUES ?p { <http://p> } "
              "VALUES ?o { \"10\"^^<http://www.w3.org/2001/XMLSchema#long> \"string\" \"bonjour\"@fr }"
              " ?s ?p ?o . }")
         (#'sparql/rewrite-values-clauses q2 {:s [(URI. "http://s1") (URI. "http://s2")]
                                            :p [(URI. "http://p")]
                                            :o [10 "string" (rdf/language "bonjour" :fr)]}))))

  (let [q3 (sparql-query "grafter/rdf/sparql/select-values-3.sparql")
        q3-ex-info (is (thrown? ExceptionInfo
                             (#'sparql/rewrite-values-clauses q3 {:o nil})))]
    (is (= (:error (ex-data q3-ex-info))
           :nil-sparql-binding))

    (is (same-query?
          (str "SELECT * WHERE {"
               "{ VALUES ?o { \"val\" } }"
               "?s ?p ?o . "
               "}")
          (#'sparql/rewrite-values-clauses q3 {:o ["val"]})))

    (is (same-query?
          (str "SELECT * WHERE {"
               "{ VALUES ?o {} }"
               "?s ?p ?o . "
               "}")
          (#'sparql/rewrite-values-clauses q3 {:o []})))

    (is (same-query?
          (str "SELECT * WHERE {"
               "{ VALUES ?o { UNDEF } }"
               "?s ?p ?o . "
               "}")
          (#'sparql/rewrite-values-clauses q3 {:o ::sparql/undef}))))

  (let [q4 (sparql-query "grafter/rdf/sparql/select-values-4.sparql")
        q4-ex-info (is (thrown? ExceptionInfo
                                (#'sparql/rewrite-values-clauses q4 {[:s :p] nil})))]
    (is (= (:error (ex-data q4-ex-info))
           :nil-sparql-binding))

    (is (same-query?
         (str "SELECT * WHERE {"
              "VALUES (?s ?p) { (<http://s1> <http://p1>) (<http://s2> <http://p2>) }"
              "?s ?p ?o ."
              "}")
         (#'sparql/rewrite-values-clauses q4 { [:s :p] [[(URI. "http://s1") (URI. "http://p1")]
                                                      [(URI. "http://s2") (URI. "http://p2")]]})))
    (is (same-query?
          (str "SELECT * WHERE {"
               "VALUES (?s ?p) { (UNDEF UNDEF) }"
               "?s ?p ?o ."
               "}")
          (#'sparql/rewrite-values-clauses q4 {[:s :p] [[::sparql/undef ::sparql/undef]]})))

    (is (same-query?
          (str "SELECT * WHERE {"
               "VALUES (?s ?p) { (UNDEF <http://p1>) (<http://s1> UNDEF) }"
               "?s ?p ?o ."
               "}")
          (#'sparql/rewrite-values-clauses q4 {[:s :p] [[::sparql/undef (URI. "http://p1")]
                                                        [(URI. "http://s1") ::sparql/undef]]})))

    (is (same-query?
          (str "SELECT * WHERE {"
               "VALUES (?s ?p) {}"
               "?s ?p ?o ."
               "}")
          (#'sparql/rewrite-values-clauses q4 {[:s :p] []})))

    (is (thrown-with-msg? AssertionError #"VALUES clause keys & vals don't match up"
          (#'sparql/rewrite-values-clauses q4 {[:s :p] [::sparql/undef]}))))

  (let [q5 (sparql-query "grafter/rdf/sparql/select-values-5.sparql")]
    (is (same-query?
         (str "SELECT * WHERE {"
              "VALUES ?o { \"7\"^^<http://www.w3.org/2001/XMLSchema#long> \"8\"^^<http://www.w3.org/2001/XMLSchema#long> \"9\"^^<http://www.w3.org/2001/XMLSchema#long> <http://new-uri> }"
              "?s ?p ?o ."
              "}")
         (#'sparql/rewrite-values-clauses q5 { :o [7 8 9 (URI. "http://new-uri")]})))))

(deftest query-test
  (let [r (repo/fixture-repo (resource "grafter/rdf/sparql/sparql-data.trig"))
        total-quads (count (into #{} r))
        spog (partial sparql/query "grafter/rdf/sparql/select-spog.sparql")]
    (testing "limits"
      (let [num-results (count (spog {:s (URI. "http://example.org/data/another-triple")
                                       ::sparql/limits {99999 2}} r))]
        (is (= 2 num-results))))

    (testing "offsets"
      (is (= 2 (count (spog {:s (URI. "http://example.org/data/another-triple")

                             ::sparql/offsets {0 1}}
                            r)))))))

(deftest strip-comments-test
  (let [q1 (sparql-query "grafter/rdf/sparql/select-prefixes-comments.sparql")]
    (is (same-query?
          (str "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
               "PREFIX foi: <http://publishmydata.com/def/ontology/foi/>"
               "SELECT * WHERE {"
               "  GRAPH ?g {"
               "    ?s ?p ?o ."
               "  }"
               "}"
               "LIMIT 7")
          (#'sparql/strip-comments q1)))))

(deftest pre-process-query-test
  (testing "the combined query pre-processing steps"
    (let [q1 (sparql-query "grafter/rdf/sparql/select-values-1.sparql")]
      (is (same-query?
            (str "SELECT * WHERE { VALUES ?s { <http://s1> <http://s2> } ?s ?p ?o . }")
            (#'sparql/pre-process-query q1 {:s [(URI. "http://s1") (URI. "http://s2")]}))))))