(ns grafter.rdf.sesame-test
  (:require
   [grafter.rdf.protocols :as pr]
   [grafter.rdf :refer [graph]]
   [grafter.rdf.ontologies.rdf :refer :all]
   [grafter.rdf.sesame :refer :all]
   [clojure.test :refer :all]
   [me.raynes.fs :as fs]))

(def test-db-path "MyDatabases/test-db")

(def ^:dynamic *test-db* nil)

(defn wrap-with-clean-test-db [f]
  (try
    (binding [*test-db* (repo (native-store test-db-path))]
      (f))
    (finally
        (fs/delete-dir test-db-path))))

(deftest with-transaction-test
  (testing "Transactions return last result of form if there's no error."
    (is (= :return-value (with-transaction *test-db*
                           :return-value))))
  (testing "Adding values in a transaction are visible after the transaction commits."
    (do
      (with-transaction *test-db*
        (pr/add *test-db* (graph "http://example.org/test/graph"
                                 ["http://test/1" [rdf:a "http://test/Test"]])))

      (is (query *test-db* "ASK WHERE { <http://test/1> ?p ?o }")))))

(deftest import-graph
  (testing "Importing graph"

    ;(import-graph test-db "http://example.org/my-graph" "drafter-live.ttl")
    )
  )

(use-fixtures :each wrap-with-clean-test-db)
