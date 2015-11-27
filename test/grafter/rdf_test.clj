(ns grafter.rdf-test
  (:require [clojure.test :refer :all]
            [grafter.rdf :refer :all]
            [grafter.rdf.protocols :refer [->Quad]]
            [grafter.tabular :refer [make-dataset]]
            [grafter.rdf.templater :refer [triplify]]))

(def test-data [["http://a1" "http://b1" "http://c1" "http://graph1"]
                ["http://a2" "http://b2" "http://c2" "http://graph2"]])

(def first-quad (->Quad "http://a1" "http://b1" "http://c1" "http://graph1"))

(def second-quad (->Quad "http://a2" "http://b2" "http://c2" "http://graph2"))

(deftest quads-test
  (testing "Quads"
    (testing "support positional destructuring"
      (let [quad (->Quad "http://subject/" "http://predicate/" "http://object/" "http://context/")
            [s p o c] quad]

        (is (= "http://subject/" s))
        (is (= "http://predicate/" p))
        (is (= "http://object/" o))
        (is (= "http://context/" c))))))

(deftest triple=-test
  (testing "triple= quads"
    (is (triple= (->Quad "http://subject/" "http://predicate/" "http://object/" "http://context/")
                 (->Quad "http://subject/" "http://predicate/" "http://object/" nil)
                 (->Quad "http://subject/" "http://predicate/" "http://object/" "http://context/2"))))

  (testing "not triple="
    (triple= (->Quad "http://subject/1" "http://predicate/" "http://object/" "http://context/")
             (->Quad "http://subject/2" "http://predicate/" "http://object/" "http://context/"))))
