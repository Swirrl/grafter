(ns grafter.core
  (:require [clojure.set :as set])
  (:require [grafter.protocols :as pr]))

(defn union
  ([] pr/empty-graph)
  ([g] g)
  ([g g2]
     (pr/union g g2))
  ([g g2 & others]
     (reduce pr/union (pr/union g g2) others)))

