(ns grafter-3.rdf4j.sparql.impl
  (:require [grafter-3.sparql.protocols :as spp]))

(defrecord Repo [query-endpoint update-endpoint])

(defrecord DefaultOpts [connectable default-opts]
  spp/Connection
  (->connection [t opts]
    (.getConnection t (merge default-opts opts))))
