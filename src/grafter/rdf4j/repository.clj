(ns grafter.rdf4j.repository
  "Functions for constructing and working with various RDF4j repositories."
  (:require [clojure.java.io :as io]
            [grafter.rdf]
            [me.raynes.fs :as fs]
            [grafter.rdf.protocols :as pr]
            [grafter.rdf4j.io :as rio]
            [clojure.tools.logging :as log]
            [grafter.rdf :as rdf]
            [clojure.string :as string]
            [grafter.rdf4j.formats :as format])
  (:import (grafter.rdf.protocols IStatement Quad)
           (java.io File)
           (java.net MalformedURLException URL)
           (java.util GregorianCalendar)
           (javax.xml.datatype DatatypeFactory)
           (org.eclipse.rdf4j.model BNode Literal Resource Statement URI
                              Value Graph)
           (org.eclipse.rdf4j.query BooleanQuery GraphQuery QueryLanguage
                              Query TupleQuery Update BindingSet)
           (org.eclipse.rdf4j.model.impl BNodeImpl BooleanLiteralImpl
                                         ContextStatementImpl
                                   IntegerLiteral LiteralImpl
                                   NumericLiteral StatementImpl
                                   URIImpl)
           (org.eclipse.rdf4j.query.impl DatasetImpl)
           (org.eclipse.rdf4j.repository Repository RepositoryConnection)
           (org.eclipse.rdf4j.repository.http HTTPRepository)
           (org.eclipse.rdf4j.repository.sail SailRepository)
           (org.eclipse.rdf4j.repository.sparql SPARQLRepository)
           (org.eclipse.rdf4j.sail Sail)
           (org.eclipse.rdf4j.sail.memory MemoryStore)
           (org.eclipse.rdf4j.sail.nativerdf NativeStore)
           (org.eclipse.rdf4j.common.iteration CloseableIteration)
           (org.eclipse.rdf4j.sail.inferencer.fc ForwardChainingRDFSInferencer
                                           DirectTypeHierarchyInferencer
                                           CustomGraphQueryInferencer)
           (org.eclipse.rdf4j.repository.event.base NotifyingRepositoryWrapper)))

(defprotocol ToConnection
  (->connection [repo] "Given an RDF4j repository return a connection to it.
  ->connection is designed to be used with the macro with-open"))

(defn- resource-array #^"[Lorg.eclipse.rdf4j.model.Resource;" [& rs]
  (into-array Resource rs))

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
     {:pre [(or (nil? triples)
                (seq triples)
                (instance? IStatement triples))]}
     (if (not (instance? IStatement triples))
       (when (seq triples)
         (let [^Iterable stmts (map rio/quad->backend-quad triples)]
           (.add this stmts (resource-array))))
       (pr/add-statement this triples))
     this)

    ([this graph triples]
     {:pre [(or (nil? triples)
                (seq triples)
                (instance? IStatement triples))]}
     (if (not (instance? IStatement triples))
       (when (seq triples)
         (let [^Iterable stmts (map rio/quad->backend-quad triples)]
           (.add this stmts (resource-array (rio/->rdf4j-uri graph)))))
       (pr/add-statement this triples))
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
  (doto (HTTPRepository. repo-url)
    (.initialize)))

(defn sparql-repo
  "Given a query-url (String or IURI) and an optional update-url String
  or IURI, return a Sesame SPARQLRepository for communicating with
  remote repositories."
  ([query-url]
     (doto (SPARQLRepository. (str query-url))
       (.initialize)))
  ([query-url update-url]
     (doto (SPARQLRepository. (str query-url)
                              (str update-url))
       (.initialize))))

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
     (.initialize))))

(defn add->repo [repo]
  (fn
    ([] (->connection repo))
    ([acc] (.close acc) repo)
    ([acc v]
     (try (if (reduced? acc)
            acc
            (rdf/add acc v))
          (catch Throwable ex
            (.close acc)
            (throw (ex-info "Exception when adding to repository" {} ex)))))))

(defn- statements-with-inferred-format [res]
  (if (seq? res)
    res
    (rdf/statements res :format (format/->rdf-format (fs/extension (str res))))))

(defn fixture-repo
  "adds the specified data to a sparql repository.  if the first
  argument is a repository that object is used, otherwise the first
  and remaining arguments are assumed to be
  grafter.rdf.protocols/itriplereadable and are loaded into a sesame
  memorystore sail-repo.

  this function is most useful for loading fixture data from files e.g.

  (fixture-repo \"test-data.trig\" \"more-test-data.trig\")"
  ([] (sail-repo))
  ([repo-or-data & data]
   (let [repo (if (instance? Repository repo-or-data)
                repo-or-data
                (let [repo (sail-repo)]
                  (with-open [conn (->connection repo)]
                    (rdf/add conn (statements-with-inferred-format repo-or-data))
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
                    (rdf/add conn (statements-with-inferred-format (io/resource repo-or-data)))

                    repo)))]
     (apply fixture-repo repo (map io/resource data)))))

(defn- query-bindings->map [^BindingSet qbs]
  (let [boundvars (.getBindingNames qbs)]
    (->> boundvars
         (mapcat (fn [k]
                   [(keyword k) (-> qbs (.getBinding k) .getValue pr/->grafter-type)]))
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
    (-> repo (.setAutoCommit false)))

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
  (pr/query-dataset [this query-str model]
    (throw-deprecated-exception!))

  pr/ISPARQLUpdateable
  (pr/update! [this query-str]
    (throw-deprecated-exception!))

  pr/ITripleReadable
  (pr/to-statements [this options]
    (throw-deprecated-exception!)))

(extend-type Graph
  pr/ITripleReadable
  (pr/to-statements [this options]
    (map rio/backend-quad->grafter-quad (iterator-seq (.match this nil nil nil (resource-array)))))

  pr/ITripleWriteable

  (pr/add-statement
    ([this graph statement]
     (.add this
           (rio/->backend-type (pr/subject statement))
           (rio/->backend-type (pr/predicate statement))
           (rio/->backend-type (pr/object statement))
           (resource-array (rio/->rdf4j-uri graph)))))

  (pr/add
    ([this triples]
     (pr/add this nil triples))

    ([this graph triples]
     (doseq [triple triples]
       (pr/add-statement this graph triple)))

    ([this graph format triple-stream]
     (pr/add this graph triple-stream))

    ([this graph base-uri format triple-stream]
     (pr/add this graph triple-stream))))

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
  (prepare-query* [this sparql-string restriction]
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
  ([repo sparql-string] (prepare-query repo sparql-string nil))
  ([repo sparql-string restriction]
   (prepare-query* repo sparql-string restriction)))

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
  (pr/query-dataset [this sparql-string dataset]
    (let [preped-query (prepare-query this sparql-string dataset)]
      (evaluate preped-query)))

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
       {:pre [(or (nil? quads)
                  (sequential? quads)
                  (instance? IStatement quads))]}
     (if (not (instance? IStatement quads))
       (when (seq quads)
         (let [^Iterable stmts
               (map rio/quad->backend-quad quads)]
                 (.remove this stmts (resource-array))))
         (pr/delete-statement this quads)))


    ([this graph triples]
       {:pre [(or (nil? triples)
                (sequential? triples)
                (instance? IStatement triples))]}
       (if (not (instance? IStatement triples))
         (when (seq triples)
           (let [^Iterable stmts (map rio/quad->backend-quad triples)]
             (.remove this stmts (resource-array (rio/->rdf4j-uri graph)))))
         (pr/delete-statement this triples)))))

(extend-protocol ToConnection
  RepositoryConnection
  (->connection [conn]
    conn)

  Repository
  (->connection [^Repository repo]
    (.getConnection repo)))

(defn make-restricted-dataset
  "Build a dataset to act as a graph restriction.  You can specify for
  both `:default-graph` and `:named-graphs`.  Both of which take sequences
  of URI strings."
  [& {:as options}]
  (let [->uri (fn [graph]
                (if (instance? URI graph)
                  graph
                  (URIImpl. graph)))]
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
  (str (reduce (fn [sb [prefix uri]]
                 (.append sb (str "PREFIX " prefix ": <" uri ">\n")))
               (StringBuffer.) prefix-map)))

(defn query
  "Run an arbitrary SPARQL query.  Works with ASK, DESCRIBE, CONSTRUCT
  and SELECT queries.

  You can call this on a Repository however if you do you may in some
  cases cause a resource leak, for example if the sequence of results
  isn't fully consumed.

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

  - `:default-graph` a seq of URI strings representing named graphs to be set
    as the default union graph for the query.

  - `:named-graphs` a seq of URI strings representing the named graphs in
    to be used in the query.

  If no options are passed then we use the default of no graph
  restrictions whilst the union graph is the union of all graphs."
  [repo sparql & {:as options :keys [prefixes]}]
  ;; we could call .setNamespace on the connection, but
  ;; connection/namespaces are mutable so better to prepend the
  ;; prefixes onto the SPARQL string ourselves.
  (let [sparql (str (build-sparql-prefixes-block prefixes) sparql)
        dataset (mapply make-restricted-dataset (or options {}))]
    (pr/query-dataset repo sparql dataset)))

(def ^:private batched-results-size 10000)

(defn- wrap-limit-offset
  "Wraps a query string with a limit/offset batching"
  ([qstr] (let [limit batched-results-size]
            (wrap-limit-offset qstr limit 0)))
  ([qstr limit offset]
   (str "SELECT * WHERE {"
        qstr
        "} LIMIT " limit " OFFSET " offset)))

(defn batched-query
  "Like query, but queries are batched from the server by wrapping
  them in a SPARQL SELECT query with a limit/offset.

  NOTE: Though this function returns a lazy sequence, it is intended
  to be used eagerly, perhaps inside something that eagerly loads the
  results and manages the connection resources inside a with-open."
  ([qstr conn] (batched-query qstr conn batched-results-size))
  ([qstr conn limit] (batched-query qstr conn limit 0))
  ([qstr conn limit offset]
   (let [res (query conn (wrap-limit-offset qstr limit offset))]
     (when (seq res)
       (lazy-cat
        res
        (batched-query qstr conn limit (+ limit offset)))))))

(extend-type RepositoryConnection

  pr/ITripleReadable
  (pr/to-statements [this {:keys [:grafter.repository/infer] :or {infer true}}]
    (let [f (fn next-item [i]
              (when (.hasNext i)
                (let [v (.next i)]
                  (lazy-seq (cons (rio/backend-quad->grafter-quad v) (next-item i))))))]
      (let [iter (.getStatements this nil nil nil infer (into-array Resource []))]
        (f iter)))))

(defn shutdown
  "Cleanly shutsdown the repository."
  [^Repository repo]
  (.shutDown repo))
