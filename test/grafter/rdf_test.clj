(ns grafter.rdf-test
  (:require [clojure.test :refer :all]
            [grafter.rdf :refer :all]
            [grafter.rdf.protocols :refer [->Quad ->Triple]]
            [grafter.tabular :refer [make-dataset graph-fn]]
            [grafter.rdf.templater :refer [graph triplify]]))

(def test-data [["http://a1" "http://b1" "http://c1" "http://graph1"]
                ["http://a2" "http://b2" "http://c2" "http://graph2"]])

(def first-quad (->Quad "http://a1" "http://b1" "http://c1" "http://graph1"))

(def second-quad (->Quad "http://a2" "http://b2" "http://c2" "http://graph2"))

(deftest quads-and-triples-test
  (testing "Triples"
    (testing "support positional destructuring"
      (let [triple (->Triple "http://subject/" "http://predicate/" "http://object/")
            [s p o] triple]

        (is (= "http://subject/" s))
        (is (= "http://predicate/" p))
        (is (= "http://object/" o)))))

  (testing "Quads"
    (testing "support positional destructuring"
      (let [quad (->Quad "http://subject/" "http://predicate/" "http://object/" "http://context/")
            [s p o c] quad]

        (is (= "http://subject/" s))
        (is (= "http://predicate/" p))
        (is (= "http://object/" o))
        (is (= "http://context/" c))))))
