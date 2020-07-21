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

  (do
    (def repo (g3s/build-repo {:query-endpoint "http://localhost:5820/cogs-dev/query" :prefixes {:qb "http://qb/"
                                                                                                 :rdf  "http://rdf/"
                                                                                                 :rdfs  "http://rdfs/"
                                                                                                 :skos "http://skos/"
                                                                                                 }}))




    (def conn (->connection repo))
    (def prepped-ask (spp/prepare-ask conn "ASK { ?s ?p ?o }" {}))
    (spp/evaluate-ask prepped-ask )

    (def prepped-cons (spp/prepare-construct conn "PREFIX owl: <http://www.w3.org/2002/07/owl#> \n prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> \n prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> \n CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o } LIMIT 10" {}))
    (iterator-seq (.iterator (spp/evaluate-construct prepped-cons)))

    (spp/evaluate-construct prepped-cons (g3s/make-rdf-handler []))


    ;; TODO fix this line it should return quads...
    (reduce conj [] (spp/evaluate-construct prepped-cons (g3s/make-rdf-handler [])))

    )




  )
