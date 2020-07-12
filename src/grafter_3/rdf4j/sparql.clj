(ns grafter-3.rdf4j.sparql
  (:require
   [grafter-3.sparql.protocols :as spp]
   [grafter-2.rdf4j.repository :as repo]
   [grafter-3.rdf4j.sparql.impl :as impl])
  (:import [org.eclipse.rdf4j.query QueryLanguage]))

(defn build-repo
  "TODO doc string about building the repo "
  [{:keys [query-endpoint update-endpoint
           username password
           http-headers quad-mode?
           session-manager
           http-client data-dir] :as opts}]

  (assoc (impl/map->Repo opts)
         ::private-repo (doto (repo/sparql-repo query-endpoint update-endpoint)
                          (.setAdditionalHttpHeaders http-headers)
                          (.setUsernameAndPassword username password)
                          (.enableQuadMode (boolean quad-mode?)))))

(defrecord PreppedAsk [prepped-ask]
  spp/EvalAsk
  (evaluate-ask [t]
    ;; TODO set other opts merged onto record e.g.   setBinding, setDataset
    (doto prepped-ask
      (.setMaxExecutionTime (:max-exection-time t))
      (.setMaxQueryTime (:max-query-time t))
      (.setIncludeInferred (:reasoning t)))
    (.evaluate prepped-ask)))

(defrecord PreppedConstruct [prepped-construct]
  spp/EvalConstruct
  (evaluate-construct [t]
    (doto prepped-construct
      (.setMaxExecutionTime (:max-exection-time t))
      (.setMaxQueryTime (:max-query-time t))
      (.setIncludeInferred (:reasoning t)))
    (.evaluate prepped-construct))
  (evaluate-construct [t rdf-handler]
    ))

(defrecord Connection [connection]
  spp/PrepareAsk
  (prepare-ask [t query-string opts]
    (merge (->PreppedAsk (.prepareBooleanQuery connection QueryLanguage/SPARQL query-string (:base-uri opts)))
           opts)))



;; TODO
;;add file, inputstream, iterable, reader, statement, (from) URL
;; transaction: begin, close, commit, rollback, isactive

;; clearNamespaces

;; close

;; enable silent mode ?
;; export statements

;; get context ids (graphs)


;; getNamespace(s)/removeNamespace (redundant due to map of :prefixes on conn)

;; get statements

;; has statement

;; is empty

;; redundant: is quad mode

;; prep boolean

;; prep graph

;; prep select/tuple

;; prep update

;; remove iterable, statement

;; set http-client

;; size

;; set parser config




(extend-protocol spp/Connection
  grafter_3.rdf4j.sparql.impl.Repo
  (->connection [t opts]
    (merge (->Connection (.getConnection (::private-repo t)))
           opts)))
