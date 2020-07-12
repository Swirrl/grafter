(ns grafter-3.sparql
  (:require [grafter-3.sparql.protocols :as spp]))

(defn ->connection
  "Given a repo returns a connection on it"
  ([repo]
   (spp/->connection repo {}))
  ([repo opts]
   (spp/->connection repo opts)))

;; todo
(defn with-defaults [thing opts]
  )

(defn prepare-ask
  ([connectable query]
   (spp/prepare-ask connectable query nil))
  ([connectable query opts]
   (spp/prepare-ask connectable query opts)))


(comment

  (require '[grafter-3.rdf4j.sparql :as g3s])


  (def repo (g3s/build-repo {:query-endpoint "http://localhost:5820/cogs-dev/query" :prefixes {:qb "http://qb/"
                                                                                                  :rdf  "http://rdf/"
                                                                                                  :rdfs  "http://rdfs/"
                                                                                                  :skos "http://skos/"
                                                                                                  }}))

  repo


  (def conn (->connection repo))
  (def prepped-ask (spp/prepare-ask conn "ASK { ?s ?p ?o }" {}))
  (spp/evaluate-ask prepped-ask )


  )
