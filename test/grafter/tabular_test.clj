(ns grafter.tabular-test
  (:require [clojure.test :refer :all]
            [grafter.tabular :refer :all]
            [grafter.tabular.csv]
            [grafter.tabular.excel]
            [incanter.core :as inc]
            [me.raynes.fs :as fs]))

(deftest header-functions-tests
  (let [raw-data [[:a :b :c] [1 2 3] [4 5 6]]]

    (testing "copy-first-row-to-header"
      (let [retval (copy-first-row-to-header raw-data)]
        (testing "returns a pair"
          (testing "where first item is the header"
            (is (= [:a :b :c] (first retval))))
          (testing "and the second item is the unmodified source data"
            (is (= raw-data (second retval)))))))

    (testing "move-first-row-to-header"
      (let [retval (move-first-row-to-header raw-data)]
        (testing "returns a pair"
          (testing "where first item is the header"
            (is (= [:a :b :c] (first retval))))
          (testing "and the second item is the source data without the first row"
            (is (= (rest raw-data) (second retval)))))))))

(deftest make-dataset-tests
  (testing "make-dataset"
    (let [raw-data [[1 2 3] [4 5 6]]]

      (testing "converts a seq of seqs into a dataset"
        (is (instance? incanter.core.Dataset
                       (make-dataset raw-data))))

      (testing "assigns column names alphabetically by default"
        (let [header (:column-names (make-dataset raw-data))]
          (is (= ["A" "B" "C"] header))))

      (testing "takes a function that extracts the column names (header row)"
        (let [dataset (make-dataset move-first-row-to-header raw-data)
              header (:column-names dataset)]

          (is (= [1 2 3] header)))))))

;; These two vars define what the content of the files
;; test/grafter/test.csv and test/grafter/test.xlsx should look like
;; when loaded.
;;
;; - CSV data is always cast as Strings
;; - Excel data when loaded is cast to floats

(def raw-csv-data [["one" "two" "three"]
                   ["1" "2" "3"]])

(def raw-excel-data [["one" "two" "three"]
                     [1.0 2.0 3.0]])

(def csv-sheet (make-dataset move-first-row-to-header raw-csv-data))

(def excel-sheet (make-dataset move-first-row-to-header raw-excel-data))

(comment
  (testing "returns a lazy-seq of all datasets beneath a path"
    (open-all-sheets)
    ))

(deftest open-tabular-file-tests
  (testing "open-tabular-file"
    (testing "Opens CSV files"
      (let [loaded-csv (open-tabular-file "./test/grafter/test.csv")]
        (testing "are a seq of seqs"
          (is (= raw-csv-data loaded-csv)))))

    (testing "Opens Excel files"
      (let [loaded-excel (open-tabular-file "./test/grafter/test.xlsx")]
        (testing "are incanter.core.Dataset records"
          (is (instance? org.apache.poi.xssf.usermodel.XSSFWorkbook loaded-excel)))))))

(deftest open-all-datasets-tests
  (testing "open-all-sheets"
    (let [sheets (open-all-datasets "./test/grafter" :make-dataset-f (partial make-dataset move-first-row-to-header))]

      (is (seq? sheets) "should yield a seq")
      (is (= 2 (count sheets)) "should be 2 datasets")

      (let [[loaded-csv-sheet loaded-excel-sheet] sheets]
        (is (= loaded-csv-sheet csv-sheet))
        (is (= loaded-excel-sheet excel-sheet))))))

(deftest make-dataset-tests
  (let [dataset (make-dataset csv-sheet)]
    (testing "make-dataset"
      (testing "makes incanter datasets."
        (is (= (inc/dataset? dataset))))

      (testing "Automatically assigns column names alphabetically if none are given"
        (let [columns (:column-names (make-dataset [(range 30)]))]
          (is (= "AA" (nth columns 26)))
          (is (= "AB" (nth columns 27))))))))
