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
              (.setBinding pq unbound-var (->sesame-rdf-type val))
              pq) preped-query bindings)
    ;; TODO consider using keywords

    (repo/evaluate preped-query)))

(comment


  (def r (repo))

  (query "grafter/rdf/sparql-data.trig"
         (->connection grafter.rdf.sparql-test/r)
         "SELECT * WHERE { ?s ?p ?o . limit 10}")

  (query "./grafter/rdf/select-spog.sparql" (->connection grafter.rdf.sparql-test/r) {"s" (java.net.URI. "http://example.org/data/a-triple")})



  )
