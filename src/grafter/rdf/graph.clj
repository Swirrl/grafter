(ns grafter.rdf.graph)

;; OLD experiments for cleaning up.


(comment
  ;; TODO consider whether we want this
  (defprotocol IGraph
    (add [this triple])
    (delete [this triple])
    (union [this other])
    (intersection [this other])
    (difference [this other])
    (subset? [this other])
    (powerset [this])
    (cardinality [this]))

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

  ;; TODO make work
  (defn graph [config_or_triple & triples]
    (let [triples (if (instance? clojure.lang.IPersistentVector config_or_triple)
                    (concat [config_or_triple] triples)
                    triples)]
      (reduce (fn [graph triple] (union graph (expand-triples triple)))
              empty-graph triples))))
