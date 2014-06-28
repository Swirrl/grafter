(ns grafter.rdf.protocols
  (:require [clojure.set :as set])
  (:import [org.openrdf.model Statement Value Resource Literal URI BNode ValueFactory]))

(defprotocol IStatement
  "An RDF triple"
  (subject [statement])
  (predicate [statement])
  (object [statement])
  (context [statement]))

(defprotocol ITripleWriteable
  (add-statement
    [this statement]
    [this graph statement])

  (add [this triples]
    [this graph triples]))

(defprotocol ITripleReadable
  "Use the higher level wrapper function statements if you just wish to read in some RDF.

  This protocol exists for implementers to hook in additional sources of statements.

  Takes a source of statements or triples and converts it into a seq
  of triples.

  A hash of options is passed to each implementation, they may be
  ignored or handled depending on the circumstance."
  (to-statements [this options]))

(defprotocol ITransactable
  "Transactions support"
  (begin [repo])
  (commit [repo])
  (rollback [repo]))

(defrecord Triple
    [s p o]
  IStatement
  (subject [s] (.s s))
  (predicate [s] (.p s))
  (object [s] (.o s))
  (context [s] nil))

(defrecord Quad
    [s p o c]
  IStatement
  (subject [s] (.s s))
  (predicate [s] (.p s))
  (object [s] (.o s))
  (context [s] (.c s)))

(extend-type clojure.lang.IPersistentVector
  IStatement
  (subject [this]
    (first this))
  (predicate [this]
    (second this))
  (object [this]
    (nth this 2)))

(defn statements
  "Attempts to coerce an arbitrary source of RDF statements into a
  sequence of grafter Statements.

  Takes optional parameters which may be used depending on the
  context e.g. specifiying the format of the source triples.

  The :format option is supplied by the wrapping function and may be
  nil, or act as an indicator about the format of the triples to read.
  Implementers can choose whether or not to ignore or require the
  format parameter."
  [this & {:keys [format] :as options}]
  (to-statements this options))

(comment
  (expand-subject [:rick
                   [:a :Person]
                   [:age 35]
                   [:married_to :katie]]))
