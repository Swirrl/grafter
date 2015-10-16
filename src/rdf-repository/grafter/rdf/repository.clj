(ns grafter.rdf.repository
  "Functions for constructing and working with various Sesame repositories."
  (:require [clojure.java.io :as io]
            [grafter.rdf.protocols :as pr]
            [grafter.rdf.io :refer :all]
            [clojure.tools.logging :as log])
  (:import (grafter.rdf.protocols IStatement Quad)
           (java.io File)
           (java.net MalformedURLException URL)
           (java.util GregorianCalendar)
           (javax.xml.datatype DatatypeFactory)
           (org.openrdf.model BNode Literal Resource Statement URI
                              Value Graph)
           (org.openrdf.query BooleanQuery GraphQuery QueryLanguage
                              Query TupleQuery Update BindingSet)
           (org.openrdf.model.impl BNodeImpl BooleanLiteralImpl
                                   CalendarLiteralImpl
                                   ContextStatementImpl
                                   IntegerLiteralImpl LiteralImpl
                                   NumericLiteralImpl StatementImpl
                                   URIImpl)
           (org.openrdf.query.impl DatasetImpl)
           (org.openrdf.repository Repository RepositoryConnection)
           (org.openrdf.repository.http HTTPRepository)
           (org.openrdf.repository.sail SailRepository)
           (org.openrdf.repository.sparql SPARQLRepository)
           (org.openrdf.sail.memory MemoryStore)
           (org.openrdf.sail.nativerdf NativeStore)
           (info.aduna.iteration CloseableIteration)
           (org.openrdf.sail.inferencer.fc ForwardChainingRDFSInferencer
                                           DirectTypeHierarchyInferencer
                                           CustomGraphQueryInferencer)))

(defprotocol ToConnection
  (->connection [repo] "Given a sesame repository return a connection to it.
  ->connection is designed to be used with the macro with-open"))

(defn- resource-array #^"[Lorg.openrdf.model.Resource;" [& rs]
  (into-array Resource rs))

(extend-type RepositoryConnection
  pr/ITripleWriteable

  (pr/add-statement
    ([this statement]
       {:pre [(instance? IStatement statement)]}
       (let [^Statement sesame-statement (IStatement->sesame-statement statement)
             resources (if-let [graph (pr/context statement)] (resource-array (URIImpl. graph)) (resource-array))]
         (doto this (.add sesame-statement resources))))

    ([this graph statement]
       {:pre [(instance? IStatement statement)]}
       (let [^Statement stm (IStatement->sesame-statement statement)
             resources (resource-array (URIImpl. graph))]
         (doto this
           (.add stm resources)))))

  (pr/add
    ([this triples]
       {:pre [(or (nil? triples)
                (sequential? triples)
                (instance? IStatement triples))]}
       (if (not (instance? IStatement triples))
         (when (seq triples)
               (let [^Iterable stmts (map IStatement->sesame-statement triples)]
                 (.add this stmts (resource-array))))
         (pr/add-statement this triples)))


    ([this graph triples]
       {:pre [(or (nil? triples)
                (sequential? triples)
                (instance? IStatement triples))]}
       (if (not (instance? IStatement triples))
         (when (seq triples)
             (let [^Iterable stmts (map IStatement->sesame-statement triples)]
               (.add this stmts (resource-array (URIImpl. graph)))))
         (pr/add-statement this triples)))

    ([this graph format triple-stream]
       (.add this triple-stream nil format (resource-array (URIImpl. graph))))

    ([this graph base-uri format triple-stream]
     (.add this triple-stream base-uri format (resource-array (URIImpl. graph))))))

(extend-type Repository
  pr/ITripleWriteable

  (pr/add-statement
    ([this statement]
       (with-open [connection (.getConnection this)]
         (log/debug "Opening connection" connection "on repo" this)
         (pr/add-statement connection statement)
         (log/debug "Closing connection" connection "on repo" this)))

    ([this graph statement]
       (with-open [connection (.getConnection this)]
         (log/debug "Opening connection" connection "on repo" this)
         (pr/add-statement (.getConnection this) graph statement)
         (log/debug "Closing connection" connection "on repo" this))))

  (pr/add
    ([this triples]
       (with-open [connection (.getConnection this)]
         (log/debug "Opening connection" connection "on repo" this)
         (pr/add connection triples)
         (log/debug "Closing connection" connection "on repo" this)))

    ([this graph triples]
       (with-open [connection (.getConnection this)]
         (log/debug "Opening connection" connection "on repo" this)
         (pr/add connection graph triples)
         (log/debug "Closing connection" connection "on repo" this)))

    ([this graph format triple-stream]
       (with-open [^RepositoryConnection connection (.getConnection this)]
         (pr/add connection graph format triple-stream)))

    ([this graph base-uri format triple-stream]
       (with-open [^RepositoryConnection connection (.getConnection this)]
         (pr/add connection graph base-uri format triple-stream)))))


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

(defn repo
  "Given a sesame Store of some type, return a sesame SailRepository."
  ([] (repo (MemoryStore.)))
  ([store]
     (doto (SailRepository. store)
       (.initialize))))

(defn- query-bindings->map [^BindingSet qbs]
  (let [boundvars (.getBindingNames qbs)]
    (->> boundvars
         (mapcat (fn [k]
                   [k (-> qbs (.getBinding k) .getValue sesame-rdf-type->type)]))
         (apply hash-map))))

(extend-protocol pr/ITransactable
  Repository
  (begin [repo]
    (-> repo .getConnection (.setAutoCommit false)))

  (commit [repo]
    (-> repo .getConnection .commit))

  (rollback [repo]
    (-> repo .getConnection .rollback))

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
  pr/ISPARQLable
  (pr/query-dataset [this query-str model]
    (pr/query-dataset (.getConnection this) query-str model))

  pr/ISPARQLUpdateable
  (pr/update! [this query-str]
    (with-open [connection (.getConnection this)]
      (pr/update! connection query-str)))

  pr/ITripleReadable
  (pr/to-statements [this options]
    (pr/to-statements (.getConnection this) options)))

(extend-type Graph
  pr/ITripleReadable
  (pr/to-statements [this options]
    (map sesame-statement->IStatement (iterator-seq (.match this nil nil nil (resource-array)))))

  pr/ITripleWriteable

  (pr/add-statement
    ([this statement]
     (pr/add-statement this nil statement))

    ([this graph statement]
     (.add this
           (->sesame-rdf-type (pr/subject statement))
           (->sesame-rdf-type (pr/predicate statement))
           (->sesame-rdf-type (pr/object statement))
           (resource-array graph))))

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
                       (if (.hasNext results)
                         (let [current-result (try
                                                (converter-f (.next results))
                                                (catch Exception e
                                                  (.close results)
                                                  (throw e)))]
                           (lazy-cat
                            [current-result]
                            (pull-query)))
                         (.close results)))]
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
    (sesame-results->seq this sesame-statement->IStatement))

  Update
  (evaluate [this]
    (.execute this)))

(defn prepare-query
  "Low level function to prepare (parse, but not process) a sesame RDF
  query.  Takes a repository a query string and an optional sesame
  Dataset to act as a query restriction.

  Prepared queries still need to be evaluated with evaluate."
  ([repo sparql-string] (prepare-query repo sparql-string nil))
  ([repo sparql-string dataset]
     (let [conn (->connection repo)]
       (let [pq (.prepareQuery conn
                               QueryLanguage/SPARQL
                               sparql-string)]

         (when dataset (.setDataset pq dataset))
         pq))))

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
  pr/ISPARQLable
  (pr/query-dataset [this sparql-string dataset]
    (let [preped-query (prepare-query this sparql-string dataset)]
      (evaluate preped-query)))

  pr/ISPARQLUpdateable
  (pr/update! [this sparql-string]
    (let [prepared-query (.prepareUpdate this
                                         QueryLanguage/SPARQL
                                         sparql-string)]
      (.execute prepared-query))))

(extend-protocol ToConnection
  RepositoryConnection
  (->connection
    [^Repository repo]
    (if (instance? RepositoryConnection repo)
      repo
      (let [c (.getConnection repo)]
        c)))

  SailRepository
  (->connection
    [^Repository repo]
    (.getConnection repo))

  SPARQLRepository
  (->connection
    [^Repository repo]
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
    (when options
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
  [repo sparql & {:as options}]
  (let [dataset (mapply make-restricted-dataset (or options {}))]
    (pr/query-dataset repo sparql dataset)))

(extend-type RepositoryConnection

  pr/ITripleReadable
  (pr/to-statements [this _]
    (map
     (fn [{:strs [s p o c]}]
       (Quad. s p o c))

     (query this "SELECT ?s ?p ?o ?c WHERE {
               GRAPH ?c {
                 ?s ?p ?o .
               }
            }"))))

(defn shutdown
  "Cleanly shutsdown the repository."
  [^Repository repo]
  (.shutDown repo))
