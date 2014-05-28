(ns grafter.sequences-test
  (:require [clojure.test :refer :all]
            [grafter.sequences :refer :all]))

(deftest column-names-seq-test
  (testing "iterates alphabet repeatedly like it's numeric"
    (is  (= ["A" "B" "AA" "AB" "BA" "BB" "AAA" "AAB"]
            (take 8 (column-names-seq "AB"))))))
