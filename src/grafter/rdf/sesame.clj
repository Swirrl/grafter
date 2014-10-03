(ns grafter.rdf.sesame
  "Grafter support and wrappers for RDF processing, built on top of
  the Sesame API (http://www.openrdf.org/)."
  (:require [clojure.java.io :as io])
  (:require [grafter.rdf.protocols :as pr])
  (:import
   [java.net URL MalformedURLException]
   [java.io File]
   [grafter.rdf.protocols IStatement Triple Quad]
   [org.openrdf.model Statement Value Resource Literal URI BNode ValueFactory]
   [org.openrdf.model.impl CalendarLiteralImpl ValueFactoryImpl URIImpl
    BooleanLiteralImpl LiteralImpl IntegerLiteralImpl NumericLiteralImpl
    StatementImpl BNodeImpl ContextStatementImpl]
   [org.openrdf.repository Repository RepositoryConnection]
   [org.openrdf.query.resultio TupleQueryResultFormat]
   [org.openrdf.repository.sail SailRepository]
   [org.openrdf.repository.http HTTPRepository]
   [org.openrdf.repository.sparql SPARQLRepository]
   [org.openrdf.sail.memory MemoryStore]
   [org.openrdf.rio Rio RDFWriter RDFHandler]
   [org.openrdf.rio.binary BinaryRDFParserFactory]
   [org.openrdf.rio.nquads NQuadsParserFactory]
   [org.openrdf.rio.ntriples NTriplesParserFactory]
   [org.openrdf.rio.n3 N3ParserFactory]
   [org.openrdf.rio.rdfjson RDFJSONParserFactory]
   [org.openrdf.rio.rdfxml RDFXMLParserFactory]
   [org.openrdf.rio.trig TriGParserFactory]
   [org.openrdf.rio.trix TriXParserFactory]
   [org.openrdf.rio.turtle TurtleParserFactory]
   [org.openrdf.sail.nativerdf NativeStore]
   [org.openrdf.query Update TupleQuery TupleQueryResult TupleQueryResultHandler BooleanQueryResultHandler BindingSet QueryLanguage BooleanQuery GraphQuery]
   [org.openrdf.query.resultio.text BooleanTextWriter]
   [org.openrdf.query.resultio.sparqljson SPARQLResultsJSONWriter]
   [org.openrdf.query.resultio.sparqlxml SPARQLResultsXMLWriter SPARQLBooleanXMLWriter]
   [org.openrdf.query.resultio.binary BinaryQueryResultWriter]
   [org.openrdf.query.resultio.text.csv SPARQLResultsCSVWriter]
   [org.openrdf.query.resultio.text.tsv SPARQLResultsTSVWriter]
   [org.openrdf.query.impl DatasetImpl]
   [javax.xml.datatype XMLGregorianCalendar DatatypeFactory]
   [java.util GregorianCalendar Date]
   [org.openrdf.rio RDFFormat]))

(extend-type Statement
  ;; Extend our IStatement protocol to Sesame's Statements for convenience.
  pr/IStatement
  (subject [this] (.getSubject this))
  (predicate [this] (.getPredicate this))
  (object [this] (.getObject this))
  (context [this] (.getContext this)))

(defprotocol ISesameRDFConverter
  (->sesame-rdf-type [this] "Convert a native type into a Sesame RDF Type")
  (sesame-rdf-type->type [this] "Convert a Sesaem RDF Type into a Native Type"))

(defn s
  "Cast a string to an RDF literal.  The second optional argument can
  either be a keyword corresponding to an RDF language tag
  e.g. :en, :en-gb, or :fr or a string or URI in which case it is
  assumed to be a URI identifying the RDF type of the literal."
  ([str]
     {:pre [(string? str)]}
     (reify Object
       (toString [_] str)
       ISesameRDFConverter
       (->sesame-rdf-type [this]
         (LiteralImpl. str))))
  ([str lang-or-uri]
     {:pre [(string? str) (or (string? lang-or-uri) (keyword? lang-or-uri) (nil? lang-or-uri) (instance? URI lang-or-uri))]}
     (reify Object
       (toString [_] str)
       ISesameRDFConverter
       (->sesame-rdf-type [this]
         (LiteralImpl.  str (if (keyword? lang-or-uri)
                              (name lang-or-uri)
                              lang-or-uri))))))

(defmulti literal-datatype->type
  "A multimethod to convert an RDF literal into a corresponding
  Clojure type.  This method can be extended to provide custom
  conversions."
  (fn [literal]
    (when-let [datatype (-> literal .getDatatype)]
      (str datatype))))

(defmethod literal-datatype->type nil [literal]
  (s (.stringValue literal) (.getLanguage literal)))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#byte" [literal]
  (.byteValue literal))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#short" [literal]
  (.shortValue literal))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#decimal" [literal]
  (.decimalValue literal))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#double" [literal]
  (.doubleValue literal))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#float" [literal]
  (.floatValue literal))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#integer" [literal]
  (.integerValue literal))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#int" [literal]
  (.intValue literal))

(defmethod literal-datatype->type "http://www.w3.org/TR/xmlschema11-2/#string" [literal]
  (s (.stringValue literal) (.getLanguage literal)))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#dateTime" [literal]
  (-> literal .calendarValue .toGregorianCalendar .getTime))

(defmethod literal-datatype->type :default [literal]
  ;; If we don't have a type conversion for it, let the sesame type
  ;; through, as it's not really up to grafter to fail the processing,
  ;; as they might just want to pass data through rather than
  ;; understand it.
  literal)

(extend-protocol ISesameRDFConverter

  java.lang.Boolean
  (->sesame-rdf-type [this]
    (BooleanLiteralImpl. this))

  (sesame-rdf-type->type [this]
    this)

  BooleanLiteralImpl
  (->sesame-rdf-type [this]
    this)

  (sesame-rdf-type->type [this]
    (.booleanValue this))

  java.lang.String
  ;; Assume URI's are the norm not strings
  (->sesame-rdf-type [this]
    (URIImpl. this))

  (sesame-rdf-type->type [this]
    this)

  URI
  (->sesame-rdf-type [this]
    this)

  (sesame-rdf-type->type [this]
    (str this))

  java.lang.Integer
  (->sesame-rdf-type [this]
    (NumericLiteralImpl. this))

  java.math.BigInteger
  (->sesame-rdf-type [this]
    (NumericLiteralImpl. this (URIImpl. "http://www.w3.org/2001/XMLSchema#integer")))

  (sesame-rdf-type->type [this]
    this)

  LiteralImpl
  (->sesame-rdf-type [this]
    this)

  (sesame-rdf-type->type [this]
    (literal-datatype->type this))

  java.lang.Float
  (->sesame-rdf-type [this]
    (NumericLiteralImpl. this))

  (sesame-rdf-type->type [this]
    this)

  java.lang.Double
  (->sesame-rdf-type [this]
    (NumericLiteralImpl. this))

  (sesame-rdf-type->type [this]
    this)

  java.lang.Long
  (->sesame-rdf-type [this]
    ;; hacky and probably a little slow but works for now
    (IntegerLiteralImpl. (BigInteger. (str this))))

  (sesame-rdf-type->type [this]
    this)

  clojure.lang.BigInt
  (->sesame-rdf-type [this]
    ;; hacky and probably a little slow but works for now
    (IntegerLiteralImpl. (BigInteger. (str this))))

  (sesame-rdf-type->type [this]
    this)

  Statement
  (->sesame-rdf-type [this]
    this)

  (sesame-rdf-type->type [this]
    this)

  Triple
  (->sesame-rdf-type [this]
    (StatementImpl. (->sesame-rdf-type (pr/subject this))
                    (->sesame-rdf-type (pr/predicate this))
                    (->sesame-rdf-type (pr/object this))))

  Quad
  (->sesame-rdf-type [this]
    (ContextStatementImpl. (->sesame-rdf-type (pr/subject this))
                           (->sesame-rdf-type (pr/predicate this))
                           (->sesame-rdf-type (pr/object this))
                           (when-let [context (pr/context this)]
                             (->sesame-rdf-type context))))

  Value
  (->sesame-rdf-type [this]
    this)

  Resource
  (->sesame-rdf-type [this]
    this)

  Literal
  (->sesame-rdf-type [this]
    this)

  (sesame-rdf-type->type [this]
    (literal-datatype->type this))

  URI
  (->sesame-rdf-type [this]
    this)

  (sesame-rdf-type->type [this]
    (str this))

  java.net.URI
  (->sesame-rdf-type [this]
    (URIImpl. (.toString this)))

  java.net.URL
  (->sesame-rdf-type [this]
    (URIImpl. (.toString this)))

  BNode
  (->sesame-rdf-type [this]
    this)

  (sesame-rdf-type->type [this]
    (-> this .getID keyword))

  BNodeImpl
  (->sesame-rdf-type [this]
    this)

  (sesame-rdf-type->type [this]
    (-> this .getID keyword))

  java.util.Date
  (->sesame-rdf-type [this]
    (let [cal (doto (GregorianCalendar.)
                (.setTime this))]
      (-> (DatatypeFactory/newInstance)
          (.newXMLGregorianCalendar cal)
          CalendarLiteralImpl.)))

  clojure.lang.Keyword
  (->sesame-rdf-type [this]
    (BNodeImpl. (name this))))

(defn IStatement->sesame-statement
  "Convert a grafter IStatement into a Sesame statement."
  [is]
  (if (:c is)
    (do
      (ContextStatementImpl. (->sesame-rdf-type (.s is))
                             (URIImpl. (.p is))
                             (->sesame-rdf-type (.o is))
                             (URIImpl. (:c is))))
    (StatementImpl. (->sesame-rdf-type (.s is))
                    (URIImpl. (.p is))
                    (->sesame-rdf-type (.o is)))))

(defn sesame-statement->IStatement
  "Convert a sesame Statement into a grafter Quad."
  [st]
  ;; TODO fix this to work properly with object & context.
  ;; context should return either nil or a URI
  ;; object should be converted to a clojure type.
  (Quad. (str (.getSubject st))
         (str (.getPredicate st))
         (sesame-rdf-type->type (.getObject st))
         (.getContext st)))

(extend-type Repository
  pr/ITripleWriteable

  (pr/add-statement
    ([this statement]
       (pr/add-statement (.getConnection this) statement))

    ([this graph statement]
       (pr/add-statement (.getConnection this) graph statement)))

  (pr/add
    ([this triples]
       (pr/add (.getConnection this) triples))

    ([this graph triples]
       (pr/add (.getConnection this) graph triples))))

(extend-type RepositoryConnection
  pr/ITripleWriteable

  (pr/add-statement
    ([this statement]
       {:pre [(instance? IStatement statement)]}
       (let [sesame-statement (IStatement->sesame-statement statement)]
         (if-let [graph (:c statement)]
           (doto this
             (.add sesame-statement
                   (into-array Resource [(URIImpl. graph)])))
           (doto this
             (.add sesame-statement
                   (into-array Resource []))))))

    ([this graph statement]
       {:pre [(instance? IStatement statement)]}
       (doto this
         (.add (IStatement->sesame-statement statement)
               (into-array Resource [(URIImpl. graph)])))))

  (pr/add
    ([this triples]
       {:pre [(or (nil? triples)
                  (sequential? triples))]}
       (if (seq triples)
         (doseq [t triples]
           (pr/add-statement this t))))

    ([this graph triples]
       (if (seq triples)
         (doseq [t triples]
           (pr/add-statement this graph t))))))

(defn rdf-serializer
  "Coerces destination into an java.io.Writer using
  clojure.java.io/writer and returns an RDFSerializer.

  Accepts also the following optional options:

  :append          If set to true it will append new values to the end of
                   the file destination (default: false).

  :format          If a String or a File are provided the format parameter
                   can be optional (in which case it will be infered from
                   the file extension).  This should be a sesame RDFFormat
                   object.

  :encoding        The character encoding to be used (default: UTF-8)"

  ([destination & {:keys [append format encoding] :or {append false
                                                       encoding "UTF-8"}}]
     (let [format (or format
                      (condp = (class destination)
                        String (Rio/getWriterFormatForFileName destination)
                        File   (Rio/getWriterFormatForFileName (str destination))
                        (throw (ex-info "Could not infer file format, please supply a :format parameter" {:error :could-not-infer-file-format :object destination}))))]
       (Rio/createWriter format
                         (io/writer destination
                                    :append append
                                    :encoding encoding)))))

(extend-protocol pr/ITripleWriteable
  RDFWriter
  (pr/add-statement [this statement]
    (.handleStatement this (->sesame-rdf-type statement)))

  (pr/add
    ([this triples]
       (if (seq triples)
         (do
           (.startRDF this)
           (doseq [t triples]
             (pr/add-statement this t))
           (.endRDF this))
         (throw (IllegalArgumentException. "This serializer does not support writing a single statement.  It should be passed a sequence of statements."))))

    ([this _graph triples]
       ;; TODO if format allows graphs we should support
       ;; them... otherwise.. ignore the graph param
       (pr/add this triples))))

(defn memory-store
  "Instantiate a sesame RDF MemoryStore."
  []
  (MemoryStore.))

(defn native-store
  "Instantiate a sesame RDF NativeStore."
  ([datadir]
     (native-store (io/file datadir) "spoc,posc,cosp"))
  ([datadir indexes]
     (NativeStore. datadir indexes)))

(defn http-repo
  "Given a URL as a String return a Sesame HTTPRepository for e.g.
  interacting with the OpenRDF Workbench."
  [repo-url]
  (doto (HTTPRepository. repo-url)
    (.initialize)))

(defn sparql-repo
  "Given a query-url and an optional update-url String return a Sesame
  SPARQLRepository for communicating with remote repositories."
  ([query-url]
     (doto (SPARQLRepository. query-url)
       (.initialize)))
  ([query-url update-url]
     (doto (SPARQLRepository. query-url update-url)
       (.initialize))))

(defn repo
  "Given a sesame Store of some type, return a sesame SailRepository."
  ([] (repo (MemoryStore.)))
  ([store]
     (doto (SailRepository. store)
       (.initialize))))

(defn load-rdf
  "Loads the specified RDF file into the supplied repository.

  Takes a String or File (specifying a path to an RDF file) a base-uri
  String and an RDFFormat."
  [connection file base-uri-str format]
  (.add connection (io/file file) base-uri-str format (into-array Resource [])))

(defn- query-bindings->map [qbs]
  (let [boundvars (.getBindingNames qbs)]
    (->> boundvars
         (mapcat (fn [k]
                   [k (-> qbs (.getBinding k) .getValue)]))
         (apply hash-map))))

(extend-protocol pr/ITransactable
  Repository
  (begin [repo]
    (-> repo .getConnection .begin))

  (commit [repo]
    (-> repo .getConnection .commit))

  (rollback [repo]
    (-> repo .getConnection .rollback))

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

(defprotocol ISPARQLable
  "Quick and dirty sparql SELECT results.  Takes a connection and query
string and returns a lazy sequence of results.

It doesn't clear up properly in all cases, for example if the sequence
isn't fully consumed you may cause a resource leak."

  ;; TODO: reimplement interfaces with proper resource handling.
  (query-dataset [this sparql-string model])

  (update! [this sparql-string]))

(extend-type Repository
  ISPARQLable
  (query-dataset [this query-str model]
    (query-dataset (.getConnection this) query-str model))

  (update! [this query-str]
    (update! (.getConnection this) query-str))

  pr/ITripleReadable
  (pr/to-statements [this options]
    (pr/to-statements (.getConnection this) options)))

(defn- sesame-results->seq
  ([prepared-query] (sesame-results->seq prepared-query identity))
  ([prepared-query converter-f]
     (let [results (.evaluate prepared-query)
           run-query (fn pull-query []
                       (if (.hasNext results)
                         (let [current-result (try
                                                (converter-f (.next results))
                                                (catch Exception e
                                                  (.close results)))]
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

(defn ->connection
  "Given a sesame repository return a connection to it."
  [repo]
  (if (instance? RepositoryConnection repo)
    repo
    (.getConnection repo)))

(defn prepare-query
  "Low level function to prepare (parse, but not process) a sesame RDF
  query.  Takes a repository a query string and an optional sesame
  Dataset to act as a query restriction.

  Prepared queries still need to be evaluated with evaluate."
  ([repo sparql-string] (prepare-query repo sparql-string nil))
  ([repo sparql-string dataset]
     (let [conn (->connection repo)]
       (doto (.prepareQuery conn
                            QueryLanguage/SPARQL
                            sparql-string)
         (.setDataset dataset)))))

(defn prepare-update
  "Prepare (parse but don't process) a SPARQL update request.

  Prepared updates still need to be evaluated with evaluate."
  ([repo sparql-update-str] (prepare-update repo sparql-update-str nil))
  ([repo sparql-update-str dataset]
     (let [conn (->connection repo)]
       (doto
           (.prepareUpdate conn
                           QueryLanguage/SPARQL
                           sparql-update-str)
         (.setDataset dataset)))))

(extend-type RepositoryConnection
  ISPARQLable
  (query-dataset [this sparql-string dataset]
    (let [preped-query (prepare-query this sparql-string dataset)]
      (evaluate preped-query)))

  (update! [this sparql-string]
    (let [prepared-query (.prepareUpdate this
                                         QueryLanguage/SPARQL
                                         sparql-string)]
      (.execute prepared-query))))



(defn- ->uri [graph]
  (if (instance? URI graph)
    graph
    (URIImpl. graph)))

(defn make-restricted-dataset
  "Build a dataset to act as a graph restriction.  You can specify for
  both :default-graph and :named-graphs.  Both of which take sequences
  of URI strings."
  [& {:as options}]
  (when options
    (let [{:keys [default-graph named-graphs]
           :or {default-graph [] named-graphs []}} options
           private-graph "urn:private-drafter-graph-to-force-restrictions-when-no-graphs-are-listed"
           dataset (DatasetImpl.)]
      (if (string? default-graph)
        (.addDefaultGraph dataset (->uri default-graph))

        (doseq [graph (conj default-graph private-graph)]
          (.addDefaultGraph dataset (->uri graph))))
      (if (string? named-graphs)
        (.addNamedGraph dataset (->uri named-graphs))
        (doseq [graph named-graphs]
          (.addNamedGraph dataset (->uri graph))))
      dataset)))

(defn- mapply [f & args]
  (apply f (apply concat (butlast args) (last args))))

(defn query
  "Takes a repo and sparql string and an optional set of k/v argument
  pairs, and executes the sparql query on the repository.

  Options are:

  :default-graph a seq of URI strings representing named graphs to be set
                 as the default union graph for the query.

  :named-graphs a seq of URI strings representing the named graphs in
  to be used in the query.

  If no options are passed then we use the default of no graph
  restrictions whilst the union graph is the union of all graphs."
  [repo sparql & {:as options}]
  (let [dataset (mapply make-restricted-dataset (or options {}))]
    (query-dataset repo sparql dataset)))

(defn ^:no-doc format->parser
  "Convert a format into a sesame parser for that format."
  [format]
  (let [table {RDFFormat/NTRIPLES NTriplesParserFactory
               RDFFormat/TRIX TriXParserFactory
               RDFFormat/TRIG TriGParserFactory
               RDFFormat/RDFXML RDFXMLParserFactory
               RDFFormat/NQUADS NQuadsParserFactory
               RDFFormat/TURTLE TurtleParserFactory
               RDFFormat/JSONLD RDFJSONParserFactory
               RDFFormat/N3 N3ParserFactory
               }
        parser-class (table format)]
    (if-not parser-class
      (throw (ex-info (str "Unsupported format: " (pr-str format)) {:type :unsupported-format})))
    (-> parser-class
        .newInstance
        .getParser)))

(defn filename->rdf-format
  "Given a filename we attempt to return an appropriate RDFFormat
  object based on the files extension."
  [fname]
  (Rio/getParserFormatForFileName fname))

(defn mimetype->rdf-format
  "Given a mimetype string we attempt to return an appropriate
  RDFFormat object based on the files extension."
  [mime-type]
  (condp = mime-type
    "application/n-triples" RDFFormat/NTRIPLES ;; Sesame doesn't yet support application/n-triples
    (Rio/getParserFormatForMIMEType mime-type)))

;; http://clj-me.cgrand.net/2010/04/02/pipe-dreams-are-not-necessarily-made-of-promises/
(defn- pipe
  "Returns a pair: a seq (the read end) and a function (the write end).
  The function can takes either no arguments to close the pipe
  or one argument which is appended to the seq. Read is blocking."
  [size]
  (let [q (java.util.concurrent.LinkedBlockingQueue. size)
        EOQ (Object.)
        NIL (Object.)
        pull (fn pull [] (lazy-seq (let [x (.take q)]
                               (when-not (= EOQ x)
                                 (cons (when-not (= NIL x) x) (pull))))))]
    [(pull) (fn put! ([] (.put q EOQ)) ([x] (.put q (or x NIL))))]))

(extend-protocol pr/ITripleReadable
  RepositoryConnection
  (pr/to-statements [this _]
    (map
     (fn [{:strs [s p o c]}]
       (Quad. s p o c))

     (query this "SELECT ?s ?p ?o ?c WHERE {
               GRAPH ?c {
                 ?s ?p ?o .
               }
            }")))

  String
  (pr/to-statements [this options]
    (try
      (pr/to-statements (URL. this) options)
      (catch MalformedURLException ex
        (pr/to-statements (File. this) options))))

  URL
  (pr/to-statements [this options]
    (pr/to-statements (io/reader this) options))

  URI
  (pr/to-statements [this options]
    (pr/to-statements (str this) options))

  File
  (pr/to-statements [this opts]
    (let [implied-format (Rio/getParserFormatForFileName (str this))
          options (if implied-format
                    (merge {:format implied-format} opts)
                    opts)]
      (pr/to-statements (io/reader this) options)))

  java.io.Reader
  ;; WARNING: This implementation is necessarily a little convoluted
  ;; as we hack around Sesame to generate a lazy sequence of results.
  ;; Sesame's parse methods always assume you want to consume the
  ;; whole file of triples, so we spawn a thread to consume through
  ;; the file and use a blocking queue of 1 element to pass elements
  ;; back into a lazy sequence on the calling thread.  The queue has a
  ;; bounded size of 1 forcing it be in lockstep with the consumer.
  ;;
  ;; NOTE also none of these functions don't really allow for proper
  ;; resource clean-up unless the whole sequence is consumed.
  ;;
  ;; So, the good news is that this means you should be able to read
  ;; and stream huge files.  The bad news is that might leak a file
  ;; handle, unless you consume the whole sequence.
  ;;
  ;; TODO: consider how to support proper resource cleanup.
  (pr/to-statements [reader { :keys [format] :as options}]
    (if-not format
      (throw (ex-info (str "The RDF format was neither specified nor inferable from this object.") {:type :no-format-supplied}))
      (let [[statements put!] (pipe 1)]
        (future
          (let [parser (doto (format->parser format)
                         (.setRDFHandler (reify RDFHandler
                                           (startRDF [this])
                                           (endRDF [this]
                                             (put!)
                                             (.close reader))
                                           (handleStatement [this statement]
                                             (put! statement))
                                           (handleComment [this comment])
                                           (handleNamespace [this prefix-str uri-str]))))]
            (try
              (.parse parser reader "http://example.org/base-uri")
              (catch Exception ex
                (put! ex)))))
        (let [read-rdf (fn read-rdf [msg]
                         (if (instance? Exception msg)
                           ;; if the other thread puts an Exception on
                           ;; the pipe, raise it here.
                           (throw (ex-info "Reading triples aborted."
                                           {:type :reading-aborted} msg))
                           (sesame-statement->IStatement msg)))]
          (map read-rdf statements))))))

(defn shutdown
  "Cleanly shutsdown the repository."
  [repo]
  (.shutDown repo))
