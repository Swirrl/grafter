(ns grafter.agregate)

;; ideas about syntactic aggregating state wrappers

(def ^:dynamic *agregators* {})

(defn sum [config]
  (let [running-total (atom (or (:initial-state config) 0))]
    (fn [v]
      (swap! running-total inc))))

(comment
  (for [ag *agregators*
        row data]
    (ag v)
    ))

(defmacro each-row->> [data & forms]
  (->> data
       )
  )


(defmacro with-aggregators [agregators & form])

(comment

  (map list '[(ag row) (ag row)] '[(f1 row) (f2 row) (f3 row)])

  (def rows [[1 "Rick" 10] [2 "Katie" 20] [3 "Bill" 100] [4 "Ric" 100]])

  (with-aggregator [sum]

    (each-row->> rows
                (nth 1)
                ))

  )
