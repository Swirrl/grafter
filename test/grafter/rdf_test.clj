(ns grafter.rdf-test
  (:require [grafter.rdf :refer :all]
            [grafter.rdf.protocols :refer [->Quad raw-value data-type-uri]]
            [clojure.test :refer :all])
  (:import [java.net URI]))

(deftest triple=-test
  (testing "triple= quads"
    (is (triple= (->Quad "http://subject/" "http://predicate/" "http://object/" "http://context/")
                 (->Quad "http://subject/" "http://predicate/" "http://object/" nil)
                 (->Quad "http://subject/" "http://predicate/" "http://object/" "http://context/2"))))

  (testing "not triple="
    (triple= (->Quad "http://subject/1" "http://predicate/" "http://object/" "http://context/")
             (->Quad "http://subject/2" "http://predicate/" "http://object/" "http://context/"))))


(deftest literal-test
  (let [lit (literal "10" "http://www.w3.org/2001/XMLSchema#byte")]
    (is (= (URI. "http://www.w3.org/2001/XMLSchema#byte") (data-type-uri lit)))
    (is (= "10" (raw-value lit)))))
