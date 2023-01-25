(ns ^{:added "0.12.1"}
 grafter-2.rdf4j.repository
  "Functions for constructing and working with various RDF4j repositories."
  (:require [clojure.java.io :as io]
            [grafter-2.rdf.protocols :as pr]
            [grafter-2.rdf4j.formats :as format]
            [grafter-2.rdf4j.io :as rio]
            [me.raynes.fs :as fs])
  (:import [org.eclipse.rdf4j.model Resource Statement IRI Value]
           [org.eclipse.rdf4j.query BindingSet BooleanQuery GraphQuery Query QueryLanguage TupleQuery Update]
           [org.eclipse.rdf4j.repository Repository RepositoryConnection]
           [org.eclipse.rdf4j.sail.inferencer.fc CustomGraphQueryInferencer DirectTypeHierarchyInferencer ForwardChainingRDFSInferencer]
           [org.eclipse.rdf4j.common.iteration CloseableIteration Iteration])
  (:import org.eclipse.rdf4j.repository.sparql.SPARQLRepository
           org.eclipse.rdf4j.http.client.SharedHttpClientSessionManager
           grafter_2.rdf.protocols.IStatement
           org.eclipse.rdf4j.query.impl.DatasetImpl
           org.eclipse.rdf4j.repository.event.base.NotifyingRepositoryWrapper
           org.eclipse.rdf4j.repository.http.HTTPRepository
           org.eclipse.rdf4j.repository.sail.SailRepository
           org.eclipse.rdf4j.sail.memory.MemoryStore
           org.eclipse.rdf4j.sail.nativerdf.NativeStore
           org.eclipse.rdf4j.model.impl.SimpleValueFactory
           org.apache.http.impl.client.HttpClients
           (java.util.concurrent Executors TimeUnit)))

(defprotocol ToConnection
  (->connection [repo] "Given an RDF4j repository return a connection to it.
  ->connection is designed to be used with the macro with-open"))

(defn- resource-array #^"[Lorg.eclipse.rdf4j.model.Resource;" [& rs]
  (into-array Resource rs))

(defn- statements? [coll]
  (or (nil? coll)
      (sequential? coll)
      (set? coll)
      (instance? IStatement coll)))

(extend-type RepositoryConnection
  pr/ITripleWriteable

  (pr/add-statement
    ([this statement]
     {:pre [(instance? IStatement statement)]}
     (let [^Statement sesame-statement (rio/quad->backend-quad statement)
           resources (if-let [graph (pr/context statement)] (resource-array (rio/->rdf4j-uri graph)) (resource-array))]
       (doto this (.add sesame-statement resources))))

    ([this graph statement]
     {:pre [(instance? IStatement statement)]}
     (let [^Statement stm (rio/quad->backend-quad statement)
           resources (resource-array (rio/->rdf4j-uri graph))]
       (doto this
         (.add stm resources)))))

  (pr/add
    ([this triples]
     {:pre [(statements? triples)]}
     (if (not (instance? IStatement triples))
       (when (seq triples)
         (let [^Iterable stmts (map rio/quad->backend-quad triples)]
           (.add this stmts (resource-array))))
       (pr/add-statement this triples))
     this)

    ([this graph triples]
     {:pre [(statements? triples)]}
     (if (not (instance? IStatement triples))
       (when (seq triples)
         (let [^Iterable stmts (map rio/quad->backend-quad triples)]
           (.add this stmts (resource-array (rio/->rdf4j-uri graph)))))
       (pr/add-statement this graph triples))
     this)

    ([this graph format triple-stream]
     (doto this
       (.add triple-stream nil format (resource-array (rio/->rdf4j-uri graph)))))

    ([this graph base-uri format triple-stream]
     (doto this
       (.add triple-stream base-uri format (resource-array (rio/->rdf4j-uri graph)))))))

(definline throw-deprecated-exception!
  "Throw a more helpful error message alerting people to the need to
  change code.

  This is technically a breaking change, but it should indicate sites
  which have a bug in them anyway."
  []
  ;; Use a definline to remove extra stack frame from output so
  ;; exception is closer to call site.
  `(throw (ex-info "This function is no longer extended to Repository.  You will need to update your code to call it on a repository connection instead."
                   {:error :deprecated-function})))

(extend-type Repository
  pr/ITripleWriteable

  (pr/add-statement
    ([this statement]
     (throw-deprecated-exception!))

    ([this graph statement]
     (throw-deprecated-exception!)))

  (pr/add
    ([this triples]
     (throw-deprecated-exception!))

    ([this graph triples]
     (throw-deprecated-exception!))

    ([this graph format triple-stream]
     (throw-deprecated-exception!))

    ([this graph base-uri format triple-stream]
     (throw-deprecated-exception!)))

  pr/ITripleDeleteable
  (pr/delete
    ([this quads]
     (throw-deprecated-exception!))
    ([this graph quads]
     (throw-deprecated-exception!))))


(defn memory-store
  "Instantiate a sesame RDF MemoryStore."
  []
  (MemoryStore.))

(defn native-store
  "Instantiate a sesame RDF NativeStore."
  ([datadir]
     (native-store datadir "spoc,posc,cosp"))
  ([datadir indexes]
     (NativeStore. (io/file datadir) indexes)))

(defn http-repo
  "Given a URL as a String return a Sesame HTTPRepository for e.g.
  interacting with the OpenRDF Workbench."
  [repo-url]
  (HTTPRepository. repo-url))


(defn make-http-client-builder
  "Returns an unbuilt apache http-client configuration.

  Takes a map of the following key value pairs:

  - `:grafter.http/max-conn-total`               Total number of concurrent connections on this http client (default 10)
  - `:grafter.http/max-conn-per-route`           Maximum number of concurrent connections allowed per endpoint (default 10)
  - `:grafter.http/conn-time-to-live`            How long until an idle TCP connection in the pool will be closed (default 60)
  - `:grafter.http/conn-time-to-live-timeunit`   Timeunits for the above (default TimeUnit/SECONDS)
  - `:grafter.http/user-agent`                   The http user agent to report to the server (default \"grafter\")
  "
  [{:grafter.http/keys [max-conn-total
                        max-conn-per-route
                        conn-time-to-live
                        conn-time-to-live-timeunit
                        user-agent]
    :or {max-conn-total 10
         max-conn-per-route 10
         conn-time-to-live 60
         conn-time-to-live-timeunit TimeUnit/SECONDS
         user-agent "grafter"
         }}]
  (-> (HttpClients/custom)
      ;;(.useSystemProperties) ;; we could do this but better to be explict in clj

      ;; NOTE there are additional
      (.setMaxConnTotal max-conn-total)
      (.setMaxConnPerRoute max-conn-per-route)
      (.setUserAgent user-agent)
      (.setConnectionTimeToLive conn-time-to-live conn-time-to-live-timeunit)))

(defn make-default-thread-pool
  "Create a new thread-pool suitable for use with RDF4j's
  SPARQLRepository.

  Takes a map of key value pairs:

  - `:grafter.http/io-thread-pool-size` (default 10) the fixed size of the thread pool to read background IO

  "
  [{:grafter.http/keys [io-thread-pool-size] :or {io-thread-pool-size 10}}]
  ;; NOTE the code here is essentially a port of the code found in
  ;; RDF4j:
  ;; https://github.com/eclipse/rdf4j/blob/0b74c317c4e7508ca4518eb9486dbc292d299d41/core/http/client/src/main/java/org/eclipse/rdf4j/http/client/SharedHttpClientSessionManager.java#L167
  (let [backing-thread-factory (Executors/defaultThreadFactory)]
    (Executors/newScheduledThreadPool ;; NOTE this operates as a fixed size pool!
     io-thread-pool-size
     (reify java.util.concurrent.ThreadFactory
       (newThread [this runnable]
         (let [thread (.newThread backing-thread-factory runnable)]
           (doto thread
             (.setName (str "grafter-http-thread-" (.getName thread))))))))))

(def ^:private default-thread-pool
  (memoize make-default-thread-pool))

(defn make-shared-session-manager
  "A default shared session manager, configuring the behaviour of the
  http connection and thread pooling used inside the HttpClient and
  RDF4j.

  Accepts a map of key value pairs:

  - `:grafter/http-client-builder` (optional) if supplied overrides the default shared HttpClientBuilder (see `make-http-client-builder`)
  - `:grafter/thread-pool` (optional) if supplied overrides the default ScheduledThreadPoolExecutor.
  "

  [{:grafter/keys [http-client-builder thread-pool] :as opts}]
  (let [thread-pool (or thread-pool (make-default-thread-pool opts))]
    (SharedHttpClientSessionManager. (.build (or http-client-builder (make-http-client-builder opts)))
                                     thread-pool)))

(def ^:private default-shared-session-manager
  ;; By default we memoize this call to ensure we share the same
  ;; pooled/connection object.
  ;;
  ;; If you don't want this you can pass your own override in to
  ;; `sparql-repo`.
  (memoize (partial make-shared-session-manager {})))

(defn sparql-repo
  "Given a query-url (String or IURI) and an optional update-url String
  or IURI, return a Sesame SPARQLRepository for communicating with
  remote repositories.

  Takes the arguments `query-url` and `update-url` for the respective
  endpoints; these arguments may be `nil`.

  Optionally takes a map of options with the following key:

  `:grafter.http/session-manager` (defaults to a shared session manager as returned from `make-shared-session-manager`)
  "
  ([query-url]
   (sparql-repo query-url nil {}))

  ([query-url update-url]
   (sparql-repo query-url update-url {}))

  ([query-url update-url {:grafter.http/keys [session-manager] :as opts}]
   (let [session-manager (or session-manager (default-shared-session-manager))]
     (doto (SPARQLRepository. (str query-url)
                              (str update-url))
       (.setHttpClientSessionManager session-manager)))))


(defn notifying-repo
  "Wrap the given repository in an RDF4j NotifyingRepositoryWrapper.
  Once wrapped you can capture events on the underlying repository.

  Supports two arities:

  - Takes just a repo to wrap.
  - Takes a repo to wrap and a boolean indicating whether to report
    deltas on operations."
  ([^Repository repo]
   (NotifyingRepositoryWrapper. repo))
  ([repo report-deltas]
   (NotifyingRepositoryWrapper. repo report-deltas)))

(defn rdfs-inferencer
  "Returns a Sesame ForwardChainingRDFSInferencer using the rules from
  the RDF Semantics Recommendation (10 February 2004).

  You can instantiate a repository with a memory store or a native
  store or with any SAIL that returns InferencerConnections.  e.g. to
  instantiate a repository with a memory-store:

  `(repo (rdfs-inferencer (memory-store)))`"

  ([]
   (ForwardChainingRDFSInferencer.))
  ([notifying-sail]
   (ForwardChainingRDFSInferencer. notifying-sail)))

(defn direct-type-inferencer
  "A forward-chaining inferencer that infers the direct-type hierarchy
  relations sesame:directSubClassOf, sesame:directSubPropertyOf and
  sesame:directType."
  ([]
   (DirectTypeHierarchyInferencer.))
  ([notifying-sail]
   (DirectTypeHierarchyInferencer. notifying-sail)))

(defn custom-query-inferencer
  "A forward-chaining inferencer that infers new statements using a
  SPARQL graph query."
  ([]
   (CustomGraphQueryInferencer.))
  ([query-text matcher-text]
   (CustomGraphQueryInferencer. QueryLanguage/SPARQL query-text matcher-text))
  ([notifying-sail query-text matcher-text]
   (CustomGraphQueryInferencer. notifying-sail QueryLanguage/SPARQL query-text matcher-text)))


(defn sail-repo
  "Given a sesame Sail of some type, return a sesame SailRepository.

  Finally you can also optionally supply a sesame sail to wrap the
  repository, which can be used to configure a sesame NativeStore.

  By default this function will return a repository initialised with a
  Sesame MemoryStore."
  ([] (sail-repo (MemoryStore.)))
  ([sail]
   (doto (SailRepository. sail)
     )))

(defn add->repo [repo]
  (fn
    ([] (->connection repo))
    ([^RepositoryConnection acc] (.close acc) repo)
    ([^RepositoryConnection acc v]
     (try (if (reduced? acc)
            acc
            (pr/add acc v))
          (catch Throwable ex
            (.close acc)
            (throw (ex-info "Exception when adding to repository" {} ex)))))))

(defn- statements-with-inferred-format [res]
  (if (seq? res)
    res
    (rio/statements res :format (format/->rdf-format (fs/extension (str res))))))

(defn fixture-repo
  "adds the specified data to a sparql repository.  if the first
  argument is a repository that object is used, otherwise the first
  and remaining arguments are assumed to be
  grafter-2.core/ITripleWriteable and are loaded into a sesame
  memorystore sail-repo.

  this function is most useful for loading fixture data from files e.g.

  (fixture-repo \"test-data.trig\" \"more-test-data.trig\")"
  ([] (sail-repo))
  ([repo-or-data & data]
   (let [repo (if (instance? Repository repo-or-data)
                repo-or-data
                (let [repo (sail-repo)]
                  (with-open [conn (->connection repo)]
                    (pr/add conn (statements-with-inferred-format repo-or-data))
                    repo)))]
     (let [xf (mapcat (fn [d]
                        (cond
                          (satisfies? pr/ITripleReadable d) (statements-with-inferred-format d)
                          (seq d) d)))]
       (transduce xf (add->repo repo) data)))))

(defn resource-repo
  "Like fixture repo but assumes all supplied data is on the java
  resource path.  For example:

  (repo/resource-repo \"grafter/rdf/sparql/sparql-data.trig\"
                      \"grafter/rdf/rdf-types.trig\")

  Will load the supplied RDF files from the resource path into a
  single memory repository for testing.

  If you want to use a custom repository the first argument can be a
  repository."
  ([] (sail-repo))
  ([repo-or-data & data]
   (let [repo (let [repo (sail-repo)]
                (if (instance? Repository repo-or-data)
                  repo-or-data
                  (with-open [conn (->connection repo)]
                    (pr/add conn (statements-with-inferred-format (io/resource repo-or-data)))

                    repo)))]
     (apply fixture-repo repo (map io/resource data)))))

(defn- query-bindings->map [^BindingSet qbs]
  (let [boundvars (.getBindingNames qbs)]
    (->> boundvars
         (mapcat (fn [k]
                   [(keyword k) (some-> qbs (.getBinding k) .getValue pr/->grafter-type)]))
         (apply hash-map))))

(extend-protocol pr/ITransactable
  Repository
  (begin [repo]
    (throw-deprecated-exception!))

  (commit [repo]
    (throw-deprecated-exception!))

  (rollback [repo]
    (throw-deprecated-exception!))

  RepositoryConnection
  (begin [repo]
    (-> repo .begin))

  (commit [repo]
    (-> repo .commit))

  (rollback [repo]
    (-> repo .rollback)))

(defmacro with-transaction
  "Wraps the given forms in a transaction on the supplied repository.
  Exceptions are rolled back on failure."
  [repo & forms]
  `(try
    (pr/begin ~repo)
    (let [return# (do ~@forms)]
      (pr/commit ~repo)
      return#)
    (catch Exception e#
      (pr/rollback ~repo)
      (throw e#))))

(extend-type Repository
  clojure.core.protocols/CollReduce
  (coll-reduce
    ([this f]
     (clojure.core.protocols/coll-reduce this f (f)))
    ([this f val]
     (with-open [c (.getConnection this)]
       (reduce f val c))))

  pr/ISPARQLable
  (pr/query-dataset
    ([this query-str model] (throw-deprecated-exception!))
    ([this query-str model opts] (throw-deprecated-exception!)))

  pr/ISPARQLUpdateable
  (pr/update! [this query-str]
    (throw-deprecated-exception!))

  pr/ITripleReadable
  (pr/to-statements [this options]
    (throw-deprecated-exception!)))

(defn ^:no-doc sesame-results->seq
  "Convert a sesame results object into a lazy sequence of results."
  ([prepared-query] (sesame-results->seq prepared-query identity))
  ([^Query prepared-query converter-f]
   (let [^CloseableIteration results (.evaluate prepared-query)
         run-query (fn pull-query []
                     (try
                       (if (.hasNext results)
                         (let [current-result (try
                                                (converter-f (.next results))
                                                (catch Exception e
                                                  (.close results)
                                                  (throw (ex-info "Error reading results" {:prepared-query prepared-query} e))))]
                           (lazy-cat
                            [current-result]
                            (pull-query)))
                         (.close results))
                       (catch Exception e
                         (throw (ex-info "Error waiting on results" {:prepared-query prepared-query} e)))))]
     (run-query))))

(defprotocol IQueryEvaluator
  (evaluate [this] "Low level protocol to evaluate a sesame RDF Query
  object, and convert the results into a grafter representation."))


(extend-protocol IQueryEvaluator
  BooleanQuery
  (evaluate [this]
    (.evaluate this))

  TupleQuery
  (evaluate [this]
    (sesame-results->seq this query-bindings->map))

  GraphQuery
  (evaluate [this]
    (sesame-results->seq this rio/backend-quad->grafter-quad))

  Update
  (evaluate [this]
    (.execute this)))

(defprotocol IPrepareQuery
  (prepare-query* ^Query [this sparql-string restriction]
    "Low level function to prepare (parse, but not process) a sesame RDF
  query.  Takes a repository a query string and an optional sesame
  Dataset to act as a query restriction.

  Prepared queries still need to be evaluated with evaluate."))

(extend-protocol IPrepareQuery
  Repository
  (prepare-query* [repo sparql-string restriction]
    (println "WARNING: prepare-query* was called on a repository not a connection.  This usage is deprecated and will be removed.")
    (let [conn (->connection repo)]
      (prepare-query* conn sparql-string restriction)))

  RepositoryConnection
  (prepare-query* [repo sparql-string restriction]
    (let [conn (->connection repo)
          pq (.prepareQuery conn
                            QueryLanguage/SPARQL
                            sparql-string)]

      (when restriction (.setDataset pq restriction))
      pq)))

(defn prepare-query
  "Low level function to prepare (parse, but not process) a sesame RDF
  query.  Takes a repository a query string and an optional sesame
  Dataset to act as a query restriction.

  Prepared queries still need to be evaluated with evaluate."
  (^Query [repo sparql-string] (prepare-query repo sparql-string nil))
  (^Query [repo sparql-string restriction]
   (prepare-query repo sparql-string restriction nil))
  (^Query [repo sparql-string restriction {:keys [reasoning?] :as opts}]
   (doto (prepare-query* repo sparql-string restriction)
     (.setIncludeInferred (or reasoning? false)))))

(defn prepare-update
  "Prepare (parse but don't process) a SPARQL update request.

  Prepared updates still need to be evaluated with evaluate."
  ([repo sparql-update-str] (prepare-update repo sparql-update-str nil))
  ([repo sparql-update-str dataset]
     (let [conn (->connection repo)]
       (let [pu (.prepareUpdate conn
                                QueryLanguage/SPARQL
                                sparql-update-str)]
         (when dataset
           (.setDataset pu dataset))

         pu))))

(extend-type RepositoryConnection
  clojure.core.protocols/CollReduce
  (coll-reduce
    ([this f]
     (reduce f (f) (pr/to-statements this {})))
    ([this f val]
     (reduce f val (pr/to-statements this {}))))

  pr/ISPARQLable
  (pr/query-dataset
    ([this sparql-string dataset]
     (pr/query-dataset this sparql-string dataset {}))
    ([this sparql-string dataset opts]
     (let [preped-query (prepare-query this sparql-string dataset opts)]
       (evaluate preped-query))))

  pr/ISPARQLUpdateable
  (pr/update! [this sparql-string]
    (let [prepared-query (.prepareUpdate this
                                         QueryLanguage/SPARQL
                                         sparql-string)]
      (.execute prepared-query)))

  pr/ITripleDeleteable
  (pr/delete-statement
    ([this statement]
       {:pre [(instance? IStatement statement)]}
     (let [^Statement sesame-statement (rio/quad->backend-quad statement)
           resources (if-let [graph (pr/context statement)] (resource-array (rio/->rdf4j-uri graph)) (resource-array))]
         (doto this (.remove sesame-statement resources))))

    ([this graph statement]
     {:pre [(instance? IStatement statement)]}
     (let [^Statement stm (rio/quad->backend-quad statement)
           resources (resource-array (rio/->rdf4j-uri graph))]
         (doto this
           (.remove stm resources)))))

  (pr/delete
    ([this quads]
       {:pre [(statements? quads)]}
     (if (not (instance? IStatement quads))
       (when (seq quads)
         (let [^Iterable stmts
               (map rio/quad->backend-quad quads)]
                 (.remove this stmts (resource-array))))
         (pr/delete-statement this quads)))


    ([this graph triples]
     {:pre [(statements? triples)]}
       (if (not (instance? IStatement triples))
         (when (seq triples)
           (let [^Iterable stmts (map rio/quad->backend-quad triples)]
             (.remove this stmts (resource-array (rio/->rdf4j-uri graph)))))
         (pr/delete-statement this graph triples)))))

(extend-protocol ToConnection
  RepositoryConnection
  (->connection [conn]
    conn)

  Repository
  (->connection [^Repository repo]
    (.getConnection repo)))

(def ^:private value-factory (SimpleValueFactory/getInstance))

(defn make-restricted-dataset
  "Build a dataset to act as a graph restriction.  You can specify for
  both `:default-graph` and `:named-graphs`.  Both of which take sequences
  of URI strings."
  [& {:as options}]
  (let [->uri (fn [graph]
                (if (instance? IRI graph)
                  graph
                  (.createIRI value-factory graph)))]
    (when (or (:named-graphs options) (:default-graph options))
      (let [{:keys [default-graph named-graphs]
             :or   {default-graph [] named-graphs []}} options
            private-graph "urn:private-graph-to-force-restrictions-when-no-graphs-are-listed"
            dataset (DatasetImpl.)]

        (if (string? default-graph)
          (.addDefaultGraph dataset (->uri default-graph))
          (doseq [graph (conj default-graph private-graph)]
            (.addDefaultGraph dataset (->uri graph))))

        (if (string? named-graphs)
          (.addNamedGraph dataset (->uri named-graphs))
          (doseq [graph named-graphs]
            (.addNamedGraph dataset (->uri graph))))
        dataset))))

(defn- mapply [f & args]
  (apply f (apply concat (butlast args) (last args))))

(defn- build-sparql-prefixes-block [prefix-map]
  (str (reduce (fn [^StringBuffer sb [prefix uri]]
                 (.append sb (str "PREFIX " prefix ": <" uri ">\n")))
               (StringBuffer.) prefix-map)))

(defn query
  "Run an arbitrary SPARQL query.  Works with ASK, DESCRIBE, CONSTRUCT
  and SELECT queries.

  To use this without leaking resources it is recommended that you
  call ->connection on your repository, inside a with-open; and then
  consume all your results inside of a nested doseq/dorun/etc...

  e.g.

  ````
  (with-open [conn (->connection repo)]
     (doseq [res (query conn \"SELECT * WHERE { ?s ?p ?o .}\")]
        (println res)))
  ````

  Takes a repo and sparql string and an optional set of k/v argument
  pairs, and executes the sparql query on the repository.

  Options are:

  - `:default-graph` a seq of URI strings representing named graphs to be set as
    the default union graph for the query.

  - `:named-graphs` a seq of URI strings representing the named graphs to be
    used in the query.

  - `:reasoning?` `true|false` whether or not reasoning/inference should be used
  in the query. DEFAULT: `false`

  If no options are passed then we use the default of no graph
  restrictions whilst the union graph is the union of all graphs."
  [conn sparql & {:as options :keys [prefixes reasoning?]}]
  ;; we could call .setNamespace on the connection, but
  ;; connection/namespaces are mutable so better to prepend the
  ;; prefixes onto the SPARQL string ourselves.
  (let [sparql (str (build-sparql-prefixes-block prefixes) sparql)
        dataset (mapply make-restricted-dataset (or options {}))]

    (pr/query-dataset conn sparql dataset options)))

(extend-type RepositoryConnection
  pr/ITripleReadable
  (pr/to-statements [this {:keys [:grafter-2.repository/infer] :or {infer true}}]
    (let [f (fn next-item [^Iteration i]
              (when (.hasNext i)
                (let [v (.next i)]
                  (lazy-seq (cons (rio/backend-quad->grafter-quad v) (next-item i))))))
          ^Resource subj nil
          ^IRI pred nil
          ^Value obj nil
          iter (.getStatements this subj pred obj (boolean infer) (resource-array))]
      (f iter))))

(defn shutdown
  "Cleanly shutsdown the repository."
  [^Repository repo]
  (.shutDown repo))
