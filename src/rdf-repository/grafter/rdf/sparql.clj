(ns grafter.rdf.sparql
  (:require [grafter.rdf :refer [statements]]
            [grafter.rdf.repository :refer [repo sparql-repo ->connection]]
            [grafter.rdf.repository :as repo]
            [grafter.rdf.io :refer [->sesame-rdf-type]]
            [clojure.java.io :refer [resource]]))

(defn query [sparql-file repo bindings]
  (let [sparql-query (slurp (resource sparql-file))
        preped-query (repo/prepare-query repo sparql-query)]
    (reduce (fn [pq [unbound-var val]]
              (.setBinding pq (name unbound-var) (->sesame-rdf-type val))
              pq) preped-query bindings)

    (repo/evaluate preped-query)))

(comment


  (def r (repo "/Users/rick/repos/grafter/test/grafter/rdf/sparql-data.trig"))

  (query "grafter/rdf/select-spog.sparql" (->connection r) {"s" (java.net.URI. "http://example.org/data/a-triple")})



  )
