(ns grafter.rdf.repository-test
  (:require [grafter.rdf.templater :refer [graph]]
            [grafter.rdf.protocols :as pr]
            [grafter.rdf.repository :refer :all]
            [grafter.rdf :refer [statements s]]
            [grafter.url :refer [->GrafterURL]]
            [grafter.rdf.formats :refer :all]
            [clojure.test :refer :all])
  (:import org.openrdf.model.impl.GraphImpl
           org.openrdf.repository.sparql.SPARQLRepository))

(deftest reading-writing-to-Graph
  (let [g (GraphImpl.)]
    (grafter.rdf/add-statement g (pr/->Quad "http://s" "http://p" "http://o" nil))

    (is (= (pr/->Quad "http://s" "http://p" "http://o" nil)
           (first (grafter.rdf/statements g))))))

(deftest with-transaction-test
  (let [test-db (repo)]
    (testing "Transactions return last result of form if there's no error."
      (is (= :return-value (with-transaction test-db
                             :return-value))))
    (testing "Adding values in a transaction are visible after the transaction commits."
      (with-transaction test-db
        (pr/add test-db (graph "http://example.org/test/graph"
                               ["http://test/subj" ["http://test/pred" "http://test/obj"]])))

      (is (query test-db "ASK WHERE { <http://test/subj> ?p ?o }")))))

(deftest sparql-repo-test
  (testing "Works with a query-url arg of type String"
    (let [repo (sparql-repo "http://localhost:3001/sparql/state")]
      (is (instance? SPARQLRepository repo))))

  (testing "Works with a query-url arg which satisfies the IURI Protocol"
    (let [repo (sparql-repo (->GrafterURL "http" "localhost" 3001 ["sparql" "state"] nil nil))]
      (is (instance? SPARQLRepository repo)))))

(defn load-rdf-types-data []
  (let [db (repo)]
    (pr/add db (statements "./test/grafter/rdf-types.ttl" :format rdf-turtle))
    db))

(deftest query-test
  (let [test-db (load-rdf-types-data)]
    (are [type f?]
        (is (f? (let [o (-> (query test-db (str "PREFIX : <http://example/> SELECT ?o WHERE {" type " :hasValue ?o . }"))
                            first
                            (get "o"))]
                  (println type o)
                  o)))

      :integer integer?
      :string (fn [v] (= "hello" (str v)))
      :date (partial instance? java.util.Date)
      :float float?
      :double (partial instance? java.lang.Double)
      :boolean (fn [v] (#{true false} v)))))
