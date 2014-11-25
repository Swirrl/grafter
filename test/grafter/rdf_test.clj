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

(deftest graph-fn-test
  (testing "graph-fn"
    (testing "destructuring"
      (are [name column-names binding-form body]
        (testing name
          (let [ds (make-dataset test-data
                                 column-names)
                f (graph-fn [binding-form]
                            body)]

            (is (= first-quad
                   (first (f ds))))))

        "by :keys"
        [:a :b :c :d] {:keys [a b c d]}
        (graph d
               [a [b c]])

        "by :strs"
        ["a" "b" "c" "d"] {:strs [a b c d]}
        (graph d
               [a [b c]])

        "by map"
        ["a" :b "c" :d] {a "a" b :b c "c" graf :d}
        (graph graf
               [a [b c]])

        "by position (vector)"
        [:a :b :c :d] [one two three graf]
        (graph graf
               [one [two three]])))

    (testing "concatenates sequences returned by each form in the body"
      (let [ds (make-dataset test-data)
                f (graph-fn [[one two three graf]]
                            (graph graf
                                   [one [two three]]))]

        (is (= [first-quad second-quad]
               (f ds)))))))

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
