(ns grafter.csv-examples
  (:use [grafter.csv]))

(def my-csv (parse-csv "./examples/high-earners-pay-2012.csv"))

(-> (parse-csv "./examples/high-earners-pay-2012.csv")
    (grep "John" 2)
    (fuse #(str %1 " " %2) 2 1)
    (columns 2 1 7)
    (rows 1 10))


;; composition of pipelines
(-> (-> (parse-csv "./examples/high-earners-pay-2012.csv")
        (grep "John" 2)
        (fuse #(str %1 " " %2) 2 1)
        (columns 2 1 7)
        (rows 1 10))
    (rows 0 1))
