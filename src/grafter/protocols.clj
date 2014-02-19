(ns grafter.protocols
  (:require [clojure.set :as set])
  (:require [clojure.math.combinatorics :as comb]))

(defprotocol IStatement
  "An RDF triple"
  (subject [statement])
  (predicate [statement])
  (object [statement]))

(defrecord Triple
    [s p o]
  IStatement
  (subject [s] (.s s))
  (predicate [s] (.p s))
  (object [s] (.o s)))

(extend-type clojure.lang.IPersistentVector
  IStatement
  (subject [this]
    (first this))
  (predicate [this]
    (second this))
  (object [this]
    (nth this 2)))

(defprotocol IGraph
  (add [me triple])
  (delete [me triple])
  (union [me other])
  (intersection [me other])
  (difference [me other])
  (subset? [me other])
  (powerset [me])
  (cardinality [me]))

(extend-type clojure.lang.IPersistentSet
  IGraph
  (add [this triple]
    (conj this triple))
  (delete [this triple]
    (disj this triple))
  (union [this other]
    (set/union this other))
  (intersection [this other]
    (set/intersection this other))
  (difference [this other]
    (set/difference this other))
  (subset? [this other]
    (set/subset? this other))
  (cardinality [this]
    (count this))
  (powerset [this]
    (set (map set (comb/subsets this)))))

(def empty-graph #{})
