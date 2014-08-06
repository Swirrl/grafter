(ns grafter.rdf.validation
  (:require [grafter.rdf]
            [grafter.rdf.protocols :as pr]
            [clojure.string :as st]))

(defn blank? [v]
  (or (nil? v) (= "" v)))

(defn has-blank? [triple]
  (or (blank? (pr/subject triple))
      (blank? (pr/predicate triple))
      (blank? (pr/object triple))))

(defn validate-triples [f triples]
  (->> triples
       (map (fn [triple]
              (if (f triple)
                triple
                (throw (Exception.
                        (str "The triple: " (print-str triple) " from row: " (-> triple meta :row) " is invalid."))))))))
