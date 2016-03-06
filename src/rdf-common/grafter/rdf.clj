(ns grafter.rdf
  "Functions and macros for creating RDF data.  Includes a small
  DSL for creating turtle-like templated forms."
  (:require [grafter.rdf.protocols :as pr]
            [grafter.rdf.protocols :refer [->Quad]]
            [potemkin.namespaces :refer [import-vars]]))

;; Force loading the required protocol implementations.  Keep separate from ns
;; definition to prevent ns refactoring tools cleaning it away.
(require '[grafter.rdf.io])

(import-vars
 [grafter.rdf.io
  language
  literal])

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

  Datasinks include sesame RDF repositories, connections and anything
  built by rdf-serializer.

  Takes an optional string/URI to use as a graph."
  ([target statement]
     (pr/add-statement target statement))
  ([target graph statement]
     (pr/add-statement target graph statement)))

(defn add
  "Adds a sequence of statements to the specified datasink.  Supports
  all the same targets as add-statement.

  Takes an optional string/URI to use as a graph.

  Returns target."
  ([target triples]
   (pr/add target triples))

  ([target graph triples]
   (pr/add target graph triples))

  ([target graph format triple-stream]
   (pr/add target graph format triple-stream))

  ([target graph base-uri format triple-stream]
   (pr/add target graph base-uri format triple-stream)))

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
  chunk size of Clojure's lazy sequences."
  [this & {:keys [format buffer-size] :as options}]
  (pr/to-statements this options))
