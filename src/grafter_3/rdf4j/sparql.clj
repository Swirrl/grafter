(ns grafter-3.rdf4j.sparql
  (:require
   [grafter-3.sparql.protocols :as spp]
   [grafter-2.rdf4j.repository :as repo]
   [grafter-3.rdf4j.sparql.impl :as impl]
   [clojure.core.protocols :as ccp])
  (:import [org.eclipse.rdf4j.query QueryLanguage]
           [org.eclipse.rdf4j.rio RDFHandler]))


;; TODO may need to reify a SPARQLRepository instead so we can set and
;; access the SPARQLProtocolSession and valueFactory etc
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

(defmacro set-when
  "Syntactic sugar over doto for calling java setters, but doesn't call
  it when the set value is ::not-found"
  [obj & forms]
  (seq (conj (->> (for [[setter & arg] forms]
                    `(let [v# ~@arg]
                       (when (not= v# ::not-found)
                         (~setter ~obj v#))))
                  (cons 'do)
                  vec)
             `~obj)))

(defn set-standard-query-opts! [qobj opts]
  (set-when qobj
            (.setMaxExecutionTime (:max-exection-time opts ::not-found))
            (.setMaxQueryTime (:max-query-time opts ::not-found))
            (.setIncludeInferred (boolean (:reasoning opts ::not-found)))))

(defrecord PreppedAsk [prepped-ask]
  spp/EvalAsk
  (evaluate-ask [record-opts]
    ;; TODO set other opts merged onto record e.g.   setBinding, setDataset
    (set-standard-query-opts! prepped-ask record-opts)
    (.evaluate prepped-ask)))

(defn make-rdf-handler
  ([coll]
   (make-rdf-handler identity coll))
  ([f coll]
   (let [finished (promise)
         user-coll (transient coll)]
     (reify RDFHandler
       (startRDF [t]
         ;(assoc! stream-state :state :started)
         )
       (endRDF [t]
         ;;(assoc! stream-state :state :stopped)
         (deliver finished :fin)
         )
       (handleComment [t comment]
         (println "comment: " comment))
       (handleNamespace [t prefix uri]
         (println "handleNamespace: " prefix uri))
       (handleStatement [t st]
         (println "handleStatement: " st)
         (conj! user-coll (f st)))

       clojure.lang.IDeref
       (deref [t]
         (when @finished
           (persistent! user-coll)))))))

(defrecord PreppedConstruct [prepped-construct]
  spp/EvalConstruct
  (evaluate-construct [record-opts]
    (set-standard-query-opts! prepped-construct record-opts)
    (.evaluate prepped-construct))

  (evaluate-construct [record-opts rdf-handler]
    (set-standard-query-opts! prepped-construct record-opts)
    (.evaluate prepped-construct rdf-handler))

  ;;clojure.lang.IReduceInit
  ccp/CollReduce
  (coll-reduce
    [coll f] (let [h (make-rdf-handler [] f)]
               (spp/evaluate-construct coll h)
                @h)


    )
  (coll-reduce [coll f val]
    (let [h (make-rdf-handler val f)]
      (spp/evaluate-construct coll h)
      @h))
  )



(defrecord Connection [connection]
  spp/PrepareAsk
  (prepare-ask [t query-string opts]
    (merge (->PreppedAsk (.prepareBooleanQuery connection QueryLanguage/SPARQL query-string (:base-uri opts)))
           opts))

  spp/PrepareConstruct
  (prepare-construct [t query-string opts]
    (merge (->PreppedConstruct (.prepareGraphQuery connection QueryLanguage/SPARQL query-string (:base-uri opts)))
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
