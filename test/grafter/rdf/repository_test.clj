(ns grafter.rdf.repository-test
  (:require [grafter.rdf.templater :refer [graph]]
            [clojure.java.io :refer [file]]
            [grafter.rdf.protocols :as pr]
            [grafter.rdf.repository :refer :all]
            [grafter.rdf :refer [statements]]
            [grafter.url :refer [->GrafterURL]]
            [grafter.rdf.formats :refer :all]
            [clojure.test :refer :all])
  (:import org.openrdf.model.impl.GraphImpl
           org.openrdf.sail.memory.MemoryStore
           org.openrdf.repository.sparql.SPARQLRepository
           java.net.URI))

(def quad-fixture-file-path "./test/grafter/rdf-types.trig")

(def triple-fixture-file-path "./test/grafter/rdf-types.ttl")

(deftest repo-test
  (is (repo= (grafter.rdf/statements quad-fixture-file-path)
             (repo quad-fixture-file-path)
             (repo quad-fixture-file-path)
             (repo quad-fixture-file-path (MemoryStore.))
             (repo (file quad-fixture-file-path)))))

(deftest repo=test
  (testing "repo-like things coerce for equality checks"
    (is (repo= (repo quad-fixture-file-path)
               (grafter.rdf/statements quad-fixture-file-path)
               quad-fixture-file-path
               (file quad-fixture-file-path))))

  (testing "Inequality"
    (is (not (repo= quad-fixture-file-path
                    triple-fixture-file-path))
        "Should not be equal because the trig file are quads and the ttl file are triples."))

  (testing "Equivalence between triple file, repo and CONSTRUCT of the same data"
    (repo= triple-fixture-file-path
           (query (repo triple-fixture-file-path)
                  "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }")))

  (testing "An empty repo is equal to an empty repo"
    (is (repo= (repo (MemoryStore.))
               (repo nil)
               (repo [])
               []
               nil))))

(deftest reading-writing-to-Graph
  (let [g (GraphImpl.)
        s (URI. "http://s")
        p (URI. "http://p")
        o (URI. "http://o")]
    (grafter.rdf/add-statement g (pr/->Quad s p o nil))

    (is (= (pr/->Quad s p o nil)
           (first (grafter.rdf/statements g))))))

(deftest with-transaction-test
  (let [test-db (repo)]
    (testing "Transactions return last result of form if there's no error."
      (is (= :return-value (with-transaction test-db
                             :return-value))))
    (testing "Adding values in a transaction are visible after the transaction commits."
      (with-transaction test-db
        (pr/add test-db (graph (URI. "http://example.org/test/graph")
                               [(URI. "http://test/subj") [(URI. "http://test/pred") (URI. "http://test/obj")]])))

      (is (query test-db "ASK WHERE { <http://test/subj> ?p ?o }")))))

(deftest sparql-repo-test
  (testing "Works with a query-url arg of type String"
    (let [repo (sparql-repo "http://localhost:3001/sparql/state")]
      (is (instance? SPARQLRepository repo))))

  (testing "Works with a query-url arg which satisfies the IURI Protocol"
    (let [repo (sparql-repo (->GrafterURL "http" "localhost" 3001 ["sparql" "state"] nil nil))]
      (is (instance? SPARQLRepository repo)))))

(defn load-rdf-types-data
  ([file]
   (let [db (repo)]
     (pr/add db (statements file))
     db)))

(deftest query-test
  (let [test-db (load-rdf-types-data triple-fixture-file-path)]
    (are [type f?]
        (is (f? (let [o (-> (query test-db (str "PREFIX : <http://example/> SELECT ?o WHERE {" type " :hasValue ?o . }"))
                            first
                            (get :o))]
                  o)))

      :integer integer?
      :string (fn [v] (= "hello" (str v)))
      :date (partial instance? java.util.Date)
      :decimal (partial instance? java.math.BigDecimal)
      :float float?
      :double (partial instance? java.lang.Double)
      :boolean (fn [v] (#{true false} v)))))

(deftest round-tripping-data-and-queries
  (testing "When loading triples from a file and round-tripping througb a SPARQL
  repo it should be identical to the one read in."

    (testing "roundtripping ttl file"
      (let [file triple-fixture-file-path]
        (is (= (set (statements (load-rdf-types-data file)))
               (set (statements file))))))

    (testing "roundtripping trig file"
      (let [file quad-fixture-file-path]
        (is (= (set (statements (load-rdf-types-data file)))
               (set (statements file))))))))
