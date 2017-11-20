(ns grafter.core
  "Abstract functions for interacting with RDF & RDF backends, such as
  RDF4j."
  (:require [grafter.vocabularies.xsd :refer :all]
            [grafter.url :refer [->java-uri]])
  (:import [java.net URI]
           [java.util Date]
           [java.sql Time]
           [org.eclipse.rdf4j.model Literal]))

;;(require '[grafter.rdf4j.io])

(defprotocol IStatement
  "An RDF triple or quad"
  (subject [statement])
  (predicate [statement])
  (object [statement])
  (context [statement]))

(defprotocol ITripleWriteable
  "This protocol is implemented by anything which you can put
  statements into."
  (add-statement
    [this statement]
    [this graph statement])

  (add
    [this quads]
    [this graph triples]
    ;; A more efficient way to add an InputStream/Reader of RDF data to the destination.
    [this graph format triple-stream]
    [this graph base-uri format triple-stream]
    "Add a seq of triples or quads to a destination.  Works with a
    sequence of IStatements an InputStream, File or Reader"))

(defprotocol ITripleDeleteable
  "This protocol can be implemented by anything which you can delete
  statements from.  For example a SPARQL Update Endpoint."

  (delete-statement [this statement]
    [this graph statement])

  (delete
    [this quads]
    [this graph triples]
    "Delete the supplied triples or quads from the destination."))

(defprotocol ITripleReadable
  "Use the higher level wrapper function statements if you just wish to read in some RDF.

  This protocol exists for implementers to hook in additional sources of statements.

  Takes a source of statements or triples and converts it into a seq
  of triples.

  A hash of options is passed to each implementation, they may be
  ignored or handled depending on the circumstance."
  (to-statements [this options]))

(defprotocol ITransactable
  "Low level protocol for transactions support.  Most users probably
  want to use grafter.rdf.sesame/with-transaction"
  (begin [repo] "Start a transaction")
  (commit [repo] "Commit a transaction")
  (rollback [repo] "Rollback a transaction"))

(defprotocol ISPARQLable
  "NOTE this protocol is intended for low-level access.  End users
  should use query instead.

  Run an arbitrary SPARQL query.  Works with `ASK`, `DESCRIBE`,
  `CONSTRUCT` and `SELECT` queries.

  You can call this on a Repository however if you do you may in some
  cases cause a resource leak, for example if the sequence of results
  isn't fully consumed.

  To use this without leaking resources it is recommended that you
  call `->connection` on your repository, inside a `with-open`; and
  then consume all your results inside of a nested `doseq`/`dorun`/etc...

  e.g.

  ````
  (with-open [conn (->connection repo)]
     (doseq [res (query conn \"SELECT * WHERE { ?s ?p ?o .}\")]
        (println res)))
  ````"
  ;; TODO: reimplement interfaces with proper resource handling.
  (query-dataset [this sparql-string model]))

(defprotocol IGrafterRDFType
  "This protocol coerces a backend RDF type, e.g. an RDF4j quad object
  into an equivalent Grafter RDF type.  For example given an RDF4j
  quad it will convert it into a Grafter Quad."
  (->grafter-type [this] "Convert a backend RDF Type into a Native Type"))

(defprotocol ISPARQLUpdateable
  (update! [this sparql-string]
    "Issue a SPARQL Update statement against the repository"))

;; TODO add literals and strings...

(defprotocol IRDFString
  (lang [this]
    "Return the strings language tag (as a clojure Keyword)"))

(defprotocol IRawValue
  (raw-value [this]
    "Returns the naked value of a literal.  For native primitive
    values e.g. a java.lang.Integer, this will return the supplied
    value (like identity).  However for more complex types such as
    LangString's it will coerce the value into a more natural
    primitive type."))

(defprotocol IDatatypeURI
  (datatype-uri [this]
    "Returns the RDF literals datatype URI as a java.net.URI."))

(def rdf:langString (URI. "http://www.w3.org/1999/02/22-rdf-syntax-ns#langString"))

;; TODO add tests to ensure that datatype-uri's etc are right
;; everywhere we do string coercions.
;;
;; https://www.w3.org/TR/rdf11-new/#literals

(extend-type String
  IRDFString
  (lang [this]
    nil)

  IRawValue
  (raw-value [this]
    this)

  IDatatypeURI
  (datatype-uri [this]
    xsd:string))

(defrecord LangString [string lang]
  IRDFString
  (lang [this]
    (:lang this))

  Object
  (toString [this]
    ;; TODO consider making this output the same as .toString on a RDF4j
    ;; Literal.  Advantage is its more consistent with RDF4j etc... The
    ;; disadvantage is that this implementation makes using str more intuitive
    (:string this))

  IRawValue

  (raw-value [this]
    (.toString this))

  IDatatypeURI
  (datatype-uri [this]
    rdf:langString))

(defn language
  "Create an RDF langauge string out of a value string and a given
  language tag.  Language tags should be keywords representing the
  country code, e.g.

  (language \"Bonsoir\" :fr)"
  [s lang]
  {:pre [(string? s)
         lang
         (keyword? lang)]}
  (->LangString s lang))

(extend-type Literal
  IRDFString
  (lang [this]
    (keyword (.orElse (.getLanguage this) nil)))

  IRawValue
  (raw-value [this]
    (.stringValue this))

  IDatatypeURI
  (datatype-uri [this]
    (URI. (str (.getDatatype this)))))

(defrecord RDFLiteral [raw-value datatype-uri]
  IRawValue
  (raw-value [this]
    (:raw-value this))

  IDatatypeURI
  (datatype-uri [this]
    (:datatype-uri this))

  IRDFString
  (lang [this]
    nil))


(defn literal
  "You can use this to declare an RDF typed literal value along with
  its URI.  Note that there are implicit coercions already defined for
  many core clojure/java datatypes, so for common datatypes you
  shounld't need this."

  [val datatype-uri]
  (->RDFLiteral (str val) (->java-uri datatype-uri)))

(extend-protocol IRawValue
  Object
  (raw-value [t]
    t)

  nil
  (raw-value [t]
    t))

(extend-protocol IDatatypeURI

  java.math.BigInteger
  (datatype-uri [t]
    (->java-uri xsd:integer))

  clojure.lang.BigInt
  (datatype-uri [t]
    (->java-uri xsd:integer))

  java.math.BigDecimal
  (datatype-uri [t]
    (->java-uri xsd:decimal))

  Boolean
  (datatype-uri [t]
    (->java-uri xsd:boolean))

  Byte
  (datatype-uri [t]
    (->java-uri xsd:byte))

  Date
  (datatype-uri [t]
    (->java-uri xsd:date))

  Time
  (datatype-uri [t]
    (->java-uri xsd:dateTime))

  Double
  (datatype-uri [t]
    (->java-uri xsd:double))

  Float
  (datatype-uri [t]
    (->java-uri xsd:float))

  Integer
  (datatype-uri [t]
    (->java-uri xsd:integer))

  Long
  (datatype-uri [t]
    (->java-uri xsd:integer))

  Short
  (datatype-uri [t]
    (->java-uri xsd:short))

  String
  (datatype-uri [t]
    (->java-uri xsd:string)))

(defn- destructure-quad [quad i default]
  (case i
    0 (:s quad)
    1 (:p quad)
    2 (:o quad)
    3 (or (:c quad) default)
    :else default))

(defrecord Quad
    [s p o c]
  IStatement
  (subject [s] (.s s))
  (predicate [s] (.p s))
  (object [s] (.o s))
  (context [s] (.c s))

  clojure.lang.Indexed
  (nth [this ^int i]
    (destructure-quad this i nil))

  (nth [this ^int i default]
    (destructure-quad this i default)))

(defn ->Triple
  "Constructs a Quad with a nil graph (context)."
  [s p o]
  (->Quad s p o nil))

(defn triple?
  "Predicate function to test if object is a valid RDF triple."
  [t]
  (if (context t)
    true
    false))

(defn map->Triple
  "Constructs a Quad from an {:s :p :o } mapwith a nil graph (context)."
  [m]
  (->Triple (:s m) (:p m) (:o m)))


(defmulti blank-node?
  "Predicate function that tests whether the supplied value is
  considered to be a blank node type."
  type)

(defmethod blank-node? clojure.lang.Keyword [_]
  true)

(defmethod blank-node? :default [_]
  false)

(defn triple=
  "Equality test for an RDF triple or quad, that checks whether the supplied RDF
  statements are equal in terms of RDFs semantics i.e. two quads will be equal
  regardless of their graph/context providing their subject, predicate and
  objects are equal.

  Like clojure.core/= this function can be applied to any number of statements."
  [& quads]
  (every? #(let [f (first quads)]
             (and (= (subject f) (subject %))
                  (= (predicate f) (predicate %))
                  (= (object f) (object %))))
          (next quads)))

(defn add-statement
  "Add an RDF statement to the target datasink.  Datasinks must
  implement `grafter.rdf.protocols/ITripleWriteable`.

  Datasinks include RDF4j RDF repositories, connections and anything
  built by rdf-writer.

  Takes an optional string/URI to use as a graph."
  ([target statement]
   (add-statement target statement)
   target)
  ([target graph statement]
   (add-statement target graph statement)
   target))

(defn add
  "Adds a sequence of statements to the specified datasink.  Supports
  all the same targets as add-statement.

  Takes an optional string/URI to use as a graph.

  Depending on the target, this function will also write any prefixes
  associated with the rdf-writer to the target.

  Returns target."
  ([target triples]
   (add target triples)
   target)

  ([target graph triples]
   (add target graph triples)
   target)

  ([target graph format triple-stream]
   (add target graph format triple-stream)
   target)

  ([target graph base-uri format triple-stream]
   (add target graph base-uri format triple-stream)
   target))

(def ^:private default-batch-size 20000)

(defn- apply-batched [target apply-fn stmts batch-size]
  (doseq [batch (partition-all batch-size stmts)]
    (apply-fn target batch))
  target)

(defn add-batched
  "Adds a collection of statements to a repository in batches. The batch size is optional and default-batch-size
   will be used if not specified. Some repository implementations cache added statements in memory until explicitly
   flushed which can cause out-of-memory errors if a large number of statements are added through add. Spliting the
   input sequence into batches limits the number of cached statements and therefore can reduce memory pressure."
  ([target triples]
   (apply-batched target add triples default-batch-size))

  ([target graph-or-triples triples-or-batch-size]
    (if (number? triples-or-batch-size)
      ;;given target triples and batch-size
      (let [triples graph-or-triples
            batch-size triples-or-batch-size]
        (apply-batched target add triples batch-size))

      ;;given target graph and triples
      (let [graph graph-or-triples
            triples triples-or-batch-size]
        (apply-batched target (fn [repo batch] (add repo graph batch)) triples default-batch-size))))

  ([target graph triples batch-size]
   (apply-batched target (fn [repo batch] (add repo graph batch)) triples batch-size)))

(defn delete
  "Deletes a sequence of statements from the specified repository.

  Takes an optional string/URI to use as a graph.

  Returns target."

  ([target quads]
   (delete target quads)
   target)

  ([target graph triples]
   (delete target graph triples)
   target))

(defn delete-batched
  "Deletes a collection of statements from a repository in batches. The batch size is optional and default-batch-size
  will be used if not specified."
  ([target quads]
    (apply-batched target delete quads default-batch-size))

  ([target graph-or-quads triples-or-batch-size]
   (if (number? triples-or-batch-size)
     ;;given repo, quads and batch size
     (let [quads graph-or-quads
           batch-size triples-or-batch-size]
       (apply-batched target delete quads batch-size))

     ;;given repo, graph and triples
     (let [graph graph-or-quads
           triples triples-or-batch-size]
       (apply-batched target (fn [repo batch] (delete repo graph batch)) triples default-batch-size))))

  ([target graph triples batch-size]
    (apply-batched target (fn [repo batch] (delete repo graph batch)) triples batch-size)))


;; TODO TOOD TODO

(defn ^:no-doc statements
  "Attempts to coerce an arbitrary source of RDF statements into a
  sequence of grafter Statements.

  If the source is a quad store quads from all the named graphs will
  be returned.  Any triples in an unnamed graph will be ignored.

  Takes optional parameters which may be used depending on the
  context e.g. specifiying the format of the source triples.

  The `:format` option is supplied by the wrapping function and may be
  nil, or act as an indicator about the format of the triples to read.
  Implementers can choose whether or not to ignore or require the
  format parameter.

  The `:buffer-size` option can be used to configure the buffer size
  at which statements are parsed from an RDF stream.  Its default
  value of 32 was found to work well in practice, and also aligns with
  chunk size of Clojure's lazy sequences.

  The `:base-uri` option can be supplied to automatically re`@base`
  URI's on a new prefix when reading."
  [this & {:keys [format buffer-size base-uri] :as options}]
  (throw (ex-info "Moved function definition.  Please use grafter.rdf4j/statements instead" {}))
  #_(to-statements this options))
