(ns grafter.rdf.repository-test
  (:require [clojure.test :refer :all]
            [grafter.rdf.repository :refer :all]
            [grafter.rdf.templater :refer [graph]]
            [grafter.rdf.protocols :as pr]
            [grafter.rdf.formats :refer :all]
            [grafter.rdf.ontologies.rdf :refer :all])
  (:import [org.openrdf.model Value]
           [org.openrdf.model.impl BNodeImpl BooleanLiteralImpl
            CalendarLiteralImpl
            ContextStatementImpl
            IntegerLiteralImpl LiteralImpl
            NumericLiteralImpl StatementImpl
            URIImpl]))

(deftest reading-writing-to-Graph
  (let [g (org.openrdf.model.impl.GraphImpl.)]
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
                               ["http://test/1" [rdf:a "http://test/Test"]])))

      (is (query test-db "ASK WHERE { <http://test/1> ?p ?o }")))))
