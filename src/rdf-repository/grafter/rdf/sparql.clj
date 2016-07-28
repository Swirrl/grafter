(ns grafter.rdf.sparql
  (:require [grafter.rdf :refer [statements]]
            [grafter.rdf.repository :refer [repo sparql-repo ->connection]]
            [grafter.rdf.repository :as repo]
            [grafter.rdf.io :refer [->sesame-rdf-type]]
            [clojure.java.io :refer [resource]]
            [clojure.string :as str]))

(defn pre-process-limit-clauses
  "look through a query string and replaces limit clauses with values supplied
  as maps against matching SPARQL ?variable names or a limit integer"
  [sparl-query limits]
  (reduce (fn [memo [key val]]
            (if-let [pattern (cond
                               (integer? key)
                               (str "(?i)LIMIT\\s+" key)

                               (or (string? key) (keyword? key))
                               (str "(?i)LIMIT\\s+\\?" (name key)))]
              (str/replace memo
                           (re-pattern pattern)
                           (str "LIMIT " val))
              memo))
          sparl-query
          limits))

(defn query
  ([sparql-file repo]
   (query sparql-file repo {}))
  ([sparql-file repo bindings]
   (let [sparql-query (slurp (resource sparql-file))
         pre-processed-qry (pre-process-limit-clauses sparql-query
                                                      (or (:limits bindings) []))
         prepped-query (repo/prepare-query repo pre-processed-qry)]
     (reduce (fn [pq [unbound-var val]]
               (.setBinding pq (name unbound-var) (->sesame-rdf-type val))
               pq)
             prepped-query
             (dissoc bindings :limits))

     (repo/evaluate prepped-query))))

(comment
  (def r (repo "/Users/rick/repos/grafter/test/grafter/rdf/sparql-data.trig"))

  (query "grafter/rdf/select-spog.sparql" (->connection r) {"s" (java.net.URI. "http://example.org/data/a-triple")})
  )
