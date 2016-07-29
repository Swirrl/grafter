(ns grafter.rdf.sparql
  (:require [grafter.rdf :refer [statements]]
            [grafter.rdf.repository :refer [repo sparql-repo ->connection]]
            [grafter.rdf.repository :as repo]
            [grafter.rdf.io :refer [->sesame-rdf-type]]
            [clojure.java.io :refer [resource]]
            [clojure.string :as str]))

(defn- pre-process-limit-clauses
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
  "Takes a string reference to a sparql-file on the resource path and
  optionally a map of bindings that should map SPARQL variables from
  your query to concrete values, allowing you to restrict and
  customise your query.

  The bindings map is optional, and if it's not provided then the
  query in the file is run as is.

  Additionally if your sparql query specifies a LIMIT or OFFSET the
  bindings map supports the special keys ::limits and ::offstets.
  Which should be maps binding identifiable limits/offsets from your
  query to new values.

  The final argument should be the repository to query.
  "
  ([sparql-file repo]
   (query sparql-file {} repo))
  ([sparql-file bindings repo]
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
  (def r (repo/fixture-repo "test/grafter/rdf/sparql-data.trig"))

  (query "grafter/rdf/select-spog-pre-processed.sparql" {:p (java.net.URI. "http://www.w3.org/1999/02/22-rdf-syntax-ns#type")} (->connection r))

  (query "grafter/rdf/select-spog.sparql" {:p (java.net.URI. "http://www.w3.org/1999/02/22-rdf-syntax-ns#type")} (->connection r))

  (query "grafter/rdf/select-spog-pre-processed.sparql" r)

  ;; partial application

  (def spog (partial query "grafter/rdf/select-spog.sparql"))

  (spog r)

  (def pog (partial spog {:s (java.net.URI. "http://example.org/data/a-triple")}))

  (pog r)



  )
