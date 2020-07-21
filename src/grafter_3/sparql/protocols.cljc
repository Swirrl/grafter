(ns grafter-3.sparql.protocols)

(defprotocol Connection
  :extend-via-metadata true
  (->connection [repo opts]))

(defprotocol ->QueryString
  :extend-via-metadata true
  (->query-string [t] "Convert an arbitrary object into a (SPARQL) Query String"))

(defprotocol PrepareAsk
  (prepare-ask [connectable query-string opts]))

(defprotocol PrepareConstruct
  (prepare-construct [connectable query-string opts]))

(defprotocol PrepareSelect
  (prepare-select [connectable query-string opts]))

(defprotocol PrepareQuery
  (prepare-query [connectable query-string opts]))

(defprotocol PrepareUpdate
  (prepare-update [connectable query-string opts]))

(defprotocol EvalAsk
  (evaluate-ask [t] "Evaluate the ASK query, returns a boolean"))

(defprotocol EvalConstruct
  (evaluate-construct
    [t]
    [t rdf-handler]
    "Evaluate the Construct query"))



;; TODO (RDF4j experimental)
(defprotocol Explain
  (explain [t]))
