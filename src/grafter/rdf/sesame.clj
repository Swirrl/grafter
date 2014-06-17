(ns grafter.rdf.sesame
  (:require [clojure.java.io :as io])
  (:require [grafter.rdf.protocols :as pr])
  (:import [grafter.rdf.protocols IStatement Triple Quad])
  (:import [org.openrdf.model Statement Value Resource Literal URI BNode ValueFactory]
           [org.openrdf.model.impl CalendarLiteralImpl ValueFactoryImpl URIImpl
            BooleanLiteralImpl LiteralImpl IntegerLiteralImpl NumericLiteralImpl
            StatementImpl BNodeImpl ContextStatementImpl]
           [org.openrdf.repository Repository RepositoryConnection]
           [org.openrdf.repository.sail SailRepository]
           [org.openrdf.sail.memory MemoryStore]
           [org.openrdf.rio Rio RDFWriter]
           [org.openrdf.sail.nativerdf NativeStore]
           [org.openrdf.query TupleQuery TupleQueryResult BindingSet QueryLanguage BooleanQuery GraphQuery]
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
  (->sesame-rdf-type [this])
  (sesame-rdf-type->type [this]))

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

  (sesame-rdf-type->type [this]
    this)

  NumericLiteralImpl
  (->sesame-rdf-type [this]
    this)

  (sesame-rdf-type->type [this]
    ;; TODO support this
    (assert false "TODO add grafter support for this type.  Need to inspect datatype URI's in order to properly cast")
    (.intValue this))

  java.math.BigInteger
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
                           (->sesame-rdf-type (pr/context this))))

  Value
  (->sesame-rdf-type [this]
    this)

  Resource
  (->sesame-rdf-type [this]
    this)

  Literal
  (->sesame-rdf-type [this]
    this)

  URI
  (->sesame-rdf-type [this]
    this)

  java.net.URI
  (->sesame-rdf-type [this]
    (URIImpl. (.toString this)))

  java.net.URL
  (->sesame-rdf-type [this]
    (URIImpl. (.toString this)))

  BNode
  (->sesame-rdf-type [this]
    this)

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

(defn IStatement->sesame-statement [is]
  (if (.c is)
    (do
      (ContextStatementImpl. (->sesame-rdf-type (.s is))
                             (URIImpl. (.p is))
                             (->sesame-rdf-type (.o is))
                             (URIImpl. (.c is))))
    (StatementImpl. (->sesame-rdf-type (.s is))
                    (URIImpl. (.p is))
                    (->sesame-rdf-type (.o is)))))

(defn sesame-statement->IStatement [st]
  ;; TODO fix this to work properly with object & context.
  ;; context should return either nil or a URI
  ;; object should be converted to a clojure type.
  (Quad. (str (.getSubject st)) (str (.getPredicate st))
         (.getObject st)
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
       (doto this
         (.add (IStatement->sesame-statement statement)
               (into-array Resource []))))
    ([this graph statement]
       {:pre [(instance? IStatement statement)]}
       (doto this
         (.add (IStatement->sesame-statement statement)
               (into-array Resource [(URIImpl. graph)])))))

  (pr/add
    ([this triples]
       (if (seq triples)
         (doseq [t triples]
           (pr/add-statement this t))
         (pr/add-statement this triples)))

    ([this graph triples]
       (if (seq triples)
         (doseq [t triples]
           (pr/add-statement this graph t))
         (pr/add-statement this graph triples)))))

(defn rdf-serializer
  "Coerces destination into an java.io.Writer using
  clojure.java.io/writer and returns an RDFSerializer."

  ([destination]
     (rdf-serializer destination (Rio/getWriterFormatForFileName destination)))
  ([destination format]
     (Rio/createWriter format (io/writer destination))))

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

(defn memory-store []
  (MemoryStore.))

(defn native-store
  ([datadir]
     (native-store (io/file datadir) "spoc,posc,cosp"))
  ([datadir indexes]
     (NativeStore. datadir indexes)))

(defn repo
  ([] (repo (MemoryStore.)))
  ([store]
     (doto (SailRepository. store)
       (.initialize))))

(defn load-rdf [connection file base-uri-str format]
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

(defmacro with-transaction [repo & forms]
  "Wraps the given forms in a transaction on the supplied repository.
  Exceptions are rolled back on failure."
  `(try
    (pr/begin ~repo)
    (let [return# (do ~@forms)]
      (pr/commit ~repo)
      return#)
    (catch Exception e#
      (pr/rollback ~repo)
      (throw e#))))

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

(defn- evaluate-tuple-query [prepared-query]
  (sesame-results->seq query-bindings->map))

(defn- evaluate-graph-query [prepared-query]
  (sesame-results->seq prepared-query sesame-statement->IStatement))

(defprotocol ISPARQLable
  "Quick and dirty sparql SELECT results.  Takes a connection and query
string and returns a lazy sequence of results.

It doesn't clear up properly in all cases, for example if the sequence
isn't fully consumed you may cause a resource leak.

TODO: reimplement with proper resource handling."
  (query [this sparql-string]))

(extend-type Repository
  ISPARQLable
  (query [this query-str]
    (query (.getConnection this) query-str))

  pr/ITripleReadable
  (pr/statements [this]
    (pr/statements (.getConnection this))))

(extend-type RepositoryConnection
  ISPARQLable
  (query [this sparql-string]
    (let [preped-query (.prepareQuery this
                                      QueryLanguage/SPARQL
                                      sparql-string)]

      (cond
       (instance? BooleanQuery preped-query) (.evaluate preped-query)
       (instance? TupleQuery preped-query) (evaluate-tuple-query preped-query)
       (instance? GraphQuery preped-query) (evaluate-graph-query preped-query))))

  pr/ITripleReadable

  (statements [this]
    (map
     (fn [{:strs [s p o c]}]
       (Quad. s p o c))

     (query this "SELECT ?s ?p ?o ?c WHERE {
               GRAPH ?c {
                 ?s ?p ?o .
               }
            }"))))
