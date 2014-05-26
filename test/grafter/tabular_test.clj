(ns grafter.tabular-test
  (:require [clojure.test :refer :all]
            [grafter.tabular :refer :all]
            [grafter.tabular.csv]
            [grafter.tabular.excel]
            [me.raynes.fs :as fs]))

(def csv-sheet [["1" "2" "3"]
                ["one" "two" "three"]])

(def excel-sheet [[1.0 2.0 3.0]
                  ["one" "two" "three"]])

(deftest open-as-table-test
  (testing "open-tabular-file"
    (testing "Opens CSV files"
      (let [loaded-csv (open-tabular-file "./test/grafter/test.csv")]
        (testing "are a seq of seqs"
          (is (= csv-sheet loaded-csv)))

        (testing "are incanter.core.Dataset records"
          (is (instance? incanter.core.Dataset loaded-csv)))))


    (testing "Opens Excel files"
      (let [loaded-csv (open-tabular-file "./test/grafter/test.xlsx")]
        (testing "are incanter.core.Dataset records"
          (is (instance? org.apache.poi.xssf.usermodel.XSSFWorkbook loaded-csv)))))))

(deftest open-all-sheets-test
  (testing "open-all-sheets"
    (let [sheets (open-all-sheets "./test/grafter")]

      (is (seq? sheets) "should yield a seq")
      (is (= 2 (count sheets)) "should be 2 datasets")

      (let [[loaded-csv-sheet loaded-excel-sheet] sheets]

        (is (= loaded-csv-sheet csv-sheet))
        (is (= loaded-excel-sheet excel-sheet))))))
