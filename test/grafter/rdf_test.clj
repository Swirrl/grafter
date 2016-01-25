(ns grafter.rdf-test
  (:require [grafter.rdf :refer :all]
            [grafter.rdf.protocols :refer [->Quad]]
            [clojure.test :refer :all]))

(deftest triple=-test
  (testing "triple= quads"
    (is (triple= (->Quad "http://subject/" "http://predicate/" "http://object/" "http://context/")
                 (->Quad "http://subject/" "http://predicate/" "http://object/" nil)
                 (->Quad "http://subject/" "http://predicate/" "http://object/" "http://context/2"))))

  (testing "not triple="
    (triple= (->Quad "http://subject/1" "http://predicate/" "http://object/" "http://context/")
             (->Quad "http://subject/2" "http://predicate/" "http://object/" "http://context/"))))
