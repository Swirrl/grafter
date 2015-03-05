(ns grafter.tabular.melt-test
  (:require [grafter.tabular :refer [column-names make-dataset]]
            [grafter.tabular.melt :refer :all]
            [clojure.test :refer :all]))

(defn datasets-equal?
  "Compares two datasets for equality ignoring the order of rows. Two datasets are considered equal if they have the same column
   names in the same order, and have the same rows in any order."
  [ds1 ds2]
  (and (= (column-names ds1) (column-names ds2))
       (= (set (:rows ds1)) (set (:rows ds2)))))

(deftest melt-test
  (testing "Melts dataset"
    (let [initial (make-dataset [[:sales 100 200 300]
                                 [:costs 500 400 300]]
                                [:measure :2012 :2013 :2014])

          melted (melt initial [:measure])
          expected (make-dataset [[:sales :2012 100]
                                  [:sales :2013 200]
                                  [:sales :2014 300]
                                  [:costs :2012 500]
                                  [:costs :2013 400]
                                  [:costs :2014 300]]
                                 [:measure :variable :value])]
      (is (datasets-equal? melted expected))))

  (testing "Melts single row dataset"
    (let [initial (make-dataset [[:sales 100 200 300]]
                                [:measure :2012 :2013 :2014])

          melted (melt initial [:measure])
          expected (make-dataset [[:sales :2012 100]
                                  [:sales :2013 200]
                                  [:sales :2014 300]]
                                 [:measure :variable :value])]
      (is (datasets-equal? melted expected)))))

(deftest melt-column-groups-test
  (testing "Melt groups of columns"
    (let [denormalised (make-dataset [[1 2 3 4 5 6 7 8]
                                      [9 10 11 12 13 14 15 16]]
                                     [:a :b, :c :d, :e :f, :g :h])

          normalised (melt-column-groups denormalised [:a :b] [:x :y])
          expected (make-dataset [[1 2 3 4]
                                  [9 10 11 12]
                                  [1 2 5 6]
                                  [9 10 13 14]
                                  [1 2 7 8]
                                  [9 10 15 16]]
                                 [:a :b :x :y])]
      (is (datasets-equal? normalised expected))))

  (testing "Throws on invalid column group size"
    (let [initial (make-dataset [[1 2 3 4]]
                                [:fixed :a :b :c])]
      (is (thrown? IllegalArgumentException (melt-column-groups initial [:fixed] [:c1 :c2]))))))
