(ns grafter.rdf-test
  (:require [grafter.rdf :refer :all]
            [grafter.tabular :refer [make-dataset]]
            [grafter.rdf.protocols :refer [->Quad]]
            [clojure.test :refer :all]))

(def test-data [["http://a1" "http://b1" "http://c1" "http://graph1"]
                ["http://a2" "http://b2" "http://c2" "http://graph2"]])

(def first-quad (->Quad "http://a1" "http://b1" "http://c1" "http://graph1"))

(def second-quad (->Quad "http://a2" "http://b2" "http://c2" "http://graph2"))

(deftest graph-fn-test
  (testing "graph-fn"
    (testing "destructuring"
      (are [name column-names binding-form body]
        (testing name
          (let [ds (make-dataset column-names
                                 test-data)
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
