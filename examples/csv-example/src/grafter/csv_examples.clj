(ns grafter.csv-examples
  (:use [grafter.csv]))

(defn csv-example []
  (-> (parse-csv "../data/high-earners-pay-2012.csv")
      (grep "John" 2)
      (fuse #(str %1 " " %2) 2 1)
      (columns 2 1 7)
      (rows 1 10)))

;; composition of pipelines
(defn pipeline-composition-example []
  (-> (-> (parse-csv "../data/high-earners-pay-2012.csv")
          (grep "John" 2)
          (fuse #(str %1 " " %2) 2 1)
          (columns 2 1 7)
          (rows 1 10))
      (rows 0 1)))

(defn normalise-example []
  (-> (parse-csv "../data/Hampshire-DataPack-Health-v1/aac_tot_count-Table 1.csv")
      (normalise [3 46])))
