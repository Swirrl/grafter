(ns grafter.sequences-test
  #_(:require [clojure.test :refer :all]
            [grafter.sequences :refer :all]))
(comment
  (deftest column-names-seq-test
    (testing "iterates alphabet repeatedly like it's numeric"
      (is  (= ["A" "B" "AA" "AB" "BA" "BB" "AAA" "AAB"]
              (take 8 (column-names-seq "AB"))))))

  (deftest fill-when-test
    (testing "fills blank strings with previous non-blank value"
      (is (= ["a" "a" "a" "b" "b"]
             (fill-when '("a" "" nil "b" nil)))))

    (testing "applies given predicate to input values"
      (is (= [3 1 1 1 4]
             (fill-when pos? [3 1 -1 0 4]))))

    (testing "fills initial nil values with given value"
      (is (= [:a :a :b]
             (fill-when (complement nil?) '(nil nil :b) :a))))))
