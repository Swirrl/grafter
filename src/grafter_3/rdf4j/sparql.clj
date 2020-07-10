(ns grafter-3.rdf4j.sparql
  (:require [grafter-2.rdf4j.repository :as repo]))


;;(alias 'sp (create-ns 'sparql))

(defn make-repo [{:keys [query-endpoint update-endpoint
                         username password
                         http-headers quad-mode?
                         session-manager
                         http-client data-dir] :as opts}]

  (doto (repo/sparql-repo username password)
    (.setAdditionalHttpHeaders http-headers)
    (.setUsernameAndPassword username password)
    (.enableQuadMode (boolean quad-mode?))))

(defprotocol ConnectionWithOpts
  (->connection [t] [t opts]))
