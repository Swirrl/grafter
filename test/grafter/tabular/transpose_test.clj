(ns grafter.tabular.transpose-test
  (:require [grafter.tabular :refer [make-dataset]]
            [grafter.tabular.transpose :refer :all]
            [clojure.test :refer :all]))

(deftest transpose-dataset-test
  (testing "transposition of a simple dataset with short rows"
    (let [ds (make-dataset [[1 2 3 4 5 6 7 8]
                            [9 10 11 12 13 14 15 16]]
                           [:a :b, :c :d, "e" :f, :g :h])

          expected-ds (make-dataset [[:b 2 10]
                                     [:c 3 11]
                                     [:d 4 12]
                                     ["e" 5 13]
                                     [:f 6 14]
                                     [:g 7 15]
                                     [:h 8 16]]
                                    [:a 1 9])]
      (is (= expected-ds (transpose ds)))
      ;; Should also be able to round trip
      (is (= ds (-> (transpose ds)
                    (transpose))))))

  (testing "transposition of dataset with long rows and row labels)"
    ; important: test rows wider than 8 items, because of
    ; changing underlying collection implementation details
    (let [ds (make-dataset [["label1" 1 2 3 4 5 6 7 8 9 10]
                            ["label2" 11 12 13 14 15 16 17 18 nil 20]
                            ["label3" 21 22 23 24 25 26 27 28 29 30]]
                           ["" :a :b :c :d :e :f :g "h" :i :j])

          expected-ds (make-dataset [[:a 1 11 21]
                                     [:b 2 12 22]
                                     [:c 3 13 23]
                                     [:d 4 14 24]
                                     [:e 5 15 25]
                                     [:f 6 16 26]
                                     [:g 7 17 27]
                                     ["h" 8 18 28]
                                     [:i 9 nil 29]
                                     [:j 10 20 30]]
                                    ["" "label1" "label2" "label3"])]
      (is (= expected-ds (transpose ds)))
      (is (= ds (-> (transpose ds)
                    (transpose)))))))





