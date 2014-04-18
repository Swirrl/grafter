(ns grafter.rdf.protocols
  (:require [clojure.set :as set])
  (:import [org.openrdf.model Statement Value Resource Literal URI BNode ValueFactory])
  (:require [clojure.math.combinatorics :as comb]))

(defprotocol IStatement
  "An RDF triple"
  (subject [statement])
  (predicate [statement])
  (object [statement])
  (context [statement]))

(defprotocol ITripleWriteable
  (add-statement [this statement])
  (add [this triples]))

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

(comment
  (expand-subject [:rick
                   [:a :Person]
                   [:age 35]
                   [:married_to :katie]]))
