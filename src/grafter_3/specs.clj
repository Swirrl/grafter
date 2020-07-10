(ns grafter-3.specs
  (:require [clojure.spec.alpha :as s]
            [grafter-3.rdf4j.sparql :as sparql]))

(s/def ::sparql/username string?)
(s/def ::sparql/password string?)
(s/def ::sparql/http-headers (s/map-of string? string?))
(s/def ::sparql/query-endpoint string?)
(s/def ::sparql/update-endpoint string?)
(s/def ::sparql/session-manager #(instance? org.eclipse.rdf4j.http.client.HttpClientSessionManager %))
(s/def ::sparql/data-dir #(instance? java.io.File %))
(s/def ::sparql/http-client #(instance? org.apache.http.client.HttpClient %))
(s/def ::sparql/quad-mode? boolean?)

(s/def ::sparql/db-opts (s/and (s/keys :opt-un [::sparql/username
                                                ::sparql/password
                                                ::sparql/http-headers
                                                ::sparql/query-endpoint
                                                ::sparql/update-endpoint
                                                ::sparql/session-manager
                                                ::sparql/data-dir
                                                ::sparql/http-client
                                                ::sparql/quad-mode?])
                               (s/or :query-endpoint (s/keys :req-un [::sparql/query-endpoint])
                                     :update-endpoint (s/keys :req-un [::sparql/update-endpoint]))))

(s/fdef sparql/make-repo :args (s/cat :db-opts ::sparql/db-opts))

(comment
  (require '[clojure.spec.test.alpha :as st])
  (st/instrument)
  )
