(ns grafter.rdf
  "Functions and macros for creating RDF data.  Includes a small
  DSL for creating turtle-like templated forms."
  (:require [grafter.rdf.protocols :as pr]
            [potemkin.namespaces :refer [import-vars]]))

;; Force loading the required protocol implementations.  Keep separate from ns
;; definition to prevent ns refactoring tools cleaning it away.
(require '[grafter.rdf4j.io])

(import-vars
 [grafter.rdf.protocols
  ->Quad
  ->Triple
  language
  lang
  literal
  triple?
  blank-node?
  datatype-uri
  raw-value])

(defn subject
  "Return the RDF subject from a statement."
  [statement]
  (pr/subject statement))

(defn predicate
  "Return the RDF predicate from a statement."
  [statement]
  (pr/predicate statement))

(defn object
  "Return the RDF object from a statement."
  [statement]
  (pr/object statement))

(defn context
  "Return the RDF context from a statement."
  [statement]
  (pr/context statement))

(defn triple=
  "Equality test for an RDF triple or quad, that checks whether the supplied RDF
  statements are equal in terms of RDFs semantics i.e. two quads will be equal
  regardless of their graph/context providing their subject, predicate and
  objects are equal.

  Like clojure.core/= this function can be applied to any number of statements."
  [& quads]
  (every? #(let [f (first quads)]
             (and (= (pr/subject f) (pr/subject %))
                  (= (pr/predicate f) (pr/predicate %))
                  (= (pr/object f) (pr/object %))))
          (next quads)))

(defn add-statement
  "Add an RDF statement to the target datasink.  Datasinks must
  implement `grafter.rdf.protocols/ITripleWriteable`.

  Datasinks include RDF4j RDF repositories, connections and anything
  built by rdf-writer.

  Takes an optional string/URI to use as a graph."
  ([target statement]
   (pr/add-statement target statement)
   target)
  ([target graph statement]
   (pr/add-statement target graph statement)
   target))

(defn add
  "Adds a sequence of statements to the specified datasink.  Supports
  all the same targets as add-statement.

  Takes an optional string/URI to use as a graph.

  Depending on the target, this function will also write any prefixes
  associated with the rdf-writer to the target.

  Returns target."
  ([target triples]
   (pr/add target triples)
   target)

  ([target graph triples]
   (pr/add target graph triples)
   target)

  ([target graph format triple-stream]
   (pr/add target graph format triple-stream)
   target)

  ([target graph base-uri format triple-stream]
   (pr/add target graph base-uri format triple-stream)
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
   (pr/delete target quads)
   target)

  ([target graph triples]
   (pr/delete target graph triples)
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

(defn statements
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
  (pr/to-statements this options))
