(ns grafter.rdf.sesame
  (:require [clojure.java.io :as io])
  (:require [grafter.rdf.protocols :as pr])
  (:import [grafter.rdf.protocols IStatement Triple])
  (:import [org.openrdf.model Statement Value Resource Literal URI BNode ValueFactory]
           [org.openrdf.model.impl CalendarLiteralImpl ValueFactoryImpl URIImpl
            LiteralImpl IntegerLiteralImpl NumericLiteralImpl StatementImpl]
           [org.openrdf.repository Repository RepositoryConnection]
           [org.openrdf.repository.sail SailRepository]
           [org.openrdf.sail.memory MemoryStore]
           [org.openrdf.sail.nativerdf NativeStore]
           [org.openrdf.query TupleQuery TupleQueryResult BindingSet QueryLanguage]
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

(defprotocol IRDFConvertObject
  (->rdf-type [this]))

(defn s
  ([str]
     (reify Object
       (toString [_] str)
       IRDFConvertObject
       (->rdf-type [this]
         (LiteralImpl. str))))
  ([str lang]
     (reify Object
       (toString [_] str)
       IRDFConvertObject
       (->rdf-type [this]
         (LiteralImpl. str lang)))))

(defrecord Str [s]
  IRDFConvertObject
  (->rdf-type [this]
    (.toString (.s this))))

(extend-protocol IRDFConvertObject
  java.lang.String
  ;; Assume URI's are the norm not strings
  (->rdf-type [this]
    (URIImpl. this))

  java.lang.Integer
  (->rdf-type [this]
    (NumericLiteralImpl. this))

  java.math.BigInteger
  (->rdf-type [this]
    (NumericLiteralImpl. this))

  java.lang.Long
  (->rdf-type [this]
    ;; hacky and probably a little slow but works for now
    (IntegerLiteralImpl. (BigInteger. (str this))))

  clojure.lang.BigInt
  (->rdf-type [this]
    ;; hacky and probably a little slow but works for now
    (IntegerLiteralImpl. (BigInteger. (str this))))

  Statement
  (->rdf-type [this]
    this)

  Value
  (->rdf-type [this]
    this)

  Resource
  (->rdf-type [this]
    this)

  Literal
  (->rdf-type [this]
    this)

  URI
  (->rdf-type [this]
    this)

  java.net.URI
  (->rdf-type [this]
    (URIImpl. (.toString this)))

  java.net.URL
  (->rdf-type [this]
    (URIImpl. (.toString this)))

  BNode
  (->rdf-type [this]
    this)

  java.util.Date
  (->rdf-type [this]
    (let [cal (doto (GregorianCalendar.)
                (.setTime this))]
      (-> (DatatypeFactory/newInstance)
          (.newXMLGregorianCalendar cal)
          CalendarLiteralImpl.))))

(defn IStatement->sesame-statement [is]
  (StatementImpl. (URIImpl. (.s is))
                  (URIImpl. (.p is))
                  (->rdf-type (.o is))))

(extend-type Repository
  pr/ITripleWriteable
  (pr/add [this triples]
    (pr/add (.getConnection this) triples)))

(defn- add-statement [repo statement]
  {:pre [(instance? IStatement statement)]}
  (.add this (IStatement->sesame-statement statement)
        nil (into-array Resource [])))

(extend-type RepositoryConnection
  pr/ITripleWriteable
  (pr/add [this triples]
    (if (seq triples)
      (doseq [t triples]
        (prn "adding t" t this)
        (add-statement this t))
      (add-statement this triples))))

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

(defprotocol ISPARQLable
  "Quick and dirty sparql results.  Takes a connection and query
string and returns a lazy sequence of results.

It doesn't clear up properly in all cases, for example if the sequence
isn't fully consumed you may cause a resource leak.

TODO: reimplement with proper resource handling."
  (query [this sparql-string]))

(extend-type Repository
  ISPARQLable
  (query [this query-str]
    (query (.getConnection this) query-str)))

(extend-type RepositoryConnection
  ISPARQLable
  (query [this sparql-string]
    (let [tuple-query (.prepareTupleQuery this
                                        QueryLanguage/SPARQL
                                        sparql-string)
        results (.evaluate tuple-query)
        run-query (fn pull-query []
                    (if (.hasNext results)
                      (let [current-result (try
                                             (query-bindings->map (.next results))
                                             (catch Exception e
                                               (.close results)))]
                        (lazy-cat
                         [current-result]
                         (pull-query)))
                      (.close results)))]
    (run-query))))
