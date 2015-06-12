(ns grafter.rdf.protocols
  "Grafter protocols and types for RDF processing")

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
    [this triples]
    [this graph triples]
    ;; A more efficient way to add an InputStream/Reader of RDF data to the destination.
    [this graph format triple-stream]
    [this graph base-uri format triple-stream]
    "Add a seq of triples or quads to a destination.  Works with a
    sequence of IStatements an InputStream, File or Reader"))

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

(defprotocol ISPARQLUpdateable
  (update! [this sparql-string]))

(defprotocol ToConnection
   (->connection [repo] "Given a sesame repository return a connection to it.
  ->connection is designed to be used with the macro with-open"))

(defn- destructure-quad [quad i default]
  (case i
    0 (:s quad)
    1 (:p quad)
    2 (:o quad)
    3 (or (:c quad) default)
    :else default))

(defrecord Triple
    [s p o]
  IStatement
  (subject [s] (.s s))
  (predicate [s] (.p s))
  (object [s] (.o s))
  (context [s] nil)

  clojure.lang.Indexed
  (nth [this ^int i]
    (destructure-quad this i nil))

  (nth [this ^int i default]
    (destructure-quad this i default)))

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

(defn quad
  "Build a quad from a graph and a grafter.rdf.protocols/Triple."
  [graph triple]
  (->Quad (subject triple)
          (predicate triple)
          (object triple)
          graph))

(extend-type clojure.lang.IPersistentVector
  IStatement
  (subject [this]
    (first this))
  (predicate [this]
    (second this))
  (object [this]
    (nth this 2))

  ;; TODO add context to this one
  )
