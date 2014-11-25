(ns grafter.rdf
  "Functions and macros for creating RDF data.  Includes a small
  DSL for creating turtle-like templated forms."
  (:require [grafter.rdf.protocols :as pr]
            [potemkin.namespaces :refer [import-vars]]
            [grafter.rdf.sesame]
            [grafter.rdf.ontologies.util]
            [grafter.tabular])
  (:import (grafter.rdf.protocols Quad Triple)
           (org.openrdf.rio RDFFormat)
           (org.openrdf.model URI)))

(import-vars
 [grafter.rdf.sesame
  s]
 [grafter.rdf.ontologies.util
  prefixer])

;; TODO move these into their own grafter.rdf.formats namespace that
;; can be reused from other namespaces.
(def format-rdf-xml RDFFormat/RDFXML)
(def format-rdf-n3 RDFFormat/N3)
(def format-rdf-ntriples RDFFormat/NTRIPLES)
(def format-rdf-nquads RDFFormat/NQUADS)
(def format-rdf-turtle RDFFormat/TURTLE)
(def format-rdf-jsonld RDFFormat/JSONLD)
(def format-rdf-trix RDFFormat/TRIX)
(def format-rdf-trig RDFFormat/TRIG)

(defn- valid-uri? [node]
  (let [types [java.lang.String java.net.URL java.net.URI URI]]
    (some (fn [n] (instance? n node)) types)))

(defn- is-literal? [node]
  (let [types [java.lang.String java.lang.Byte java.lang.Short
               java.math.BigDecimal java.lang.Double java.lang.Float
               java.math.BigInteger java.lang.Integer java.lang.Long
               java.lang.Boolean java.util.Date]]
    (some (fn [n] (instance? n node)) types)))

(defn- valid-subject? [node]
  (or (valid-uri? node)
      (= clojure.lang.Keyword
         (type node))))

(defn- valid-predicate? [node]
  (valid-uri? node))

(defn- valid-object? [object]
  (if (and (vector? object)
           (seq object))
    (let [[[p o]] object]
      (and (valid-predicate? p)
           (valid-object? o)))
    (or (valid-uri? object)
        (is-literal? object))))

(defn- make-triples [subject predicate object-or-nested-subject]
  {:pre [(valid-subject? subject)
         (valid-predicate? predicate)
         (valid-object? object-or-nested-subject)]}
  (if (vector? object-or-nested-subject)
    (let [bnode-resource (keyword (gensym "bnode"))
          nested-pairs object-or-nested-subject]
      (-> (mapcat (partial make-triples bnode-resource)
                  (map first nested-pairs)
                  (map second nested-pairs))
          (conj (Triple. subject predicate bnode-resource))))

    (let [object object-or-nested-subject]
      [(Triple. subject predicate object)])))

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

(defn quad
  "Build a quad from a graph and a grafter.rdf.protocols/Triple."
  [graph triple]
  (Quad. (subject triple)
         (predicate triple)
         (object triple)
         graph))

(defn- expand-subj
  "Takes a turtle like data structure, like that passed to graph and
  converts it to triples."
  [[subject & po-pairs]]

  (mapcat (fn [[predicate object]]
            (make-triples subject predicate object)) po-pairs))

(defn triplify
  "Takes many turtle like structures and converts them to a lazy-seq
  of grafter.rdf.protocols.IStatement's.  Users should generally tend
  to prefer using graph to triplify."
  [& subjects]
  (mapcat expand-subj subjects))

(defn graph
  "Takes a graph-uri and a turtle-like template of vectors and returns
  a lazy-sequence of quad Statements.  A turtle-like template should
  be structured like this:

  [subject [predicate1 object1]
           [predicate2 object2]
           [predicate3 [[blank-node-predicate blank-node-object]]]]

  Subjects, predicates and objects can be strings, URI's or URL's,
  whilst objects can also be literal types such as java numeric types,
  Dates etc.

  For convenience strings in these templates are assumed to be URI's
  and are cast as such, as URI's are the most common type in linked
  data.  If you want an RDF string you should use the s function to
  build one."
  [graph-uri & triples]
  (map (partial quad graph-uri)
       (apply triplify triples)))

(defn add-properties
  "Appends the key/value pairs from the supplied hash-map into the
  triple-template form.  Assumes it is given a vector representing a
  single subject."
  [triple-template hash-map]
  (reduce conj triple-template
          (mapcat vector hash-map)))

(defn ^:no-doc get-column-by-number*
  "This function is intended for use by the graph-fn macro only, and
  should not be considered part of this namespaces public interface.
  It is only public because it is used by a macro."
  [ds row index]
  (let [col-name (grafter.tabular/resolve-column-id ds index ::not-found)]
    (if-not (= col-name ::not-found)
      (get row col-name ::not-found))))

(defn- generate-vector-bindings [ds-symbol row-symbol row-bindings]
  (let [bindings (->> row-bindings
                      (map-indexed (fn [index binding]
                                     [binding `(get-column-by-number* ~ds-symbol ~row-symbol ~index)]))
                      (apply concat)
                      (apply vector))]
    bindings))

(defn- splice-supplied-bindings [row-sym row-bindings]
  `[~row-bindings ~row-sym])

(defmacro graph-fn
  "A macro that defines an anonymous function to convert a tabular
  dataset into a graph of RDF quads.  Ultimately it converts a
  lazy-seq of rows inside a dataset, into a lazy-seq of RDF
  Statements.

  The function body should be composed of any number of forms, each of
  which should return a sequence of RDF quads.  These will then be
  concatenated together into a flattened lazy-seq of RDF statements.

  Rows are passed to the function one at a time as hash-maps, which
  can be destructured via Clojure's standard destructuring syntax.

  Additionally destructuring can be done on row-indicies (when a
  vector form is supplied) or column names (when a hash-map form is
  supplied)."

  [[row-bindings] & forms]
  {:pre [(or (symbol? row-bindings) (map? row-bindings)
             (vector? row-bindings))]}
  (let [row-sym (gensym "row")
        ds-sym (gensym "ds")]
    `(fn graphify-dataset [~ds-sym]
       (letfn [(graphify-row# [~row-sym]
                 (let ~(if (vector? row-bindings)
                         (generate-vector-bindings ds-sym row-sym row-bindings)
                         (splice-supplied-bindings row-sym row-bindings))
                   (->> (concat ~@forms)
                        (map (fn with-row-meta [triple#]
                               (with-meta triple# {::row ~row-sym}))))))]

         (mapcat graphify-row# (:rows ~ds-sym))))))


(defn add-statement
  "Add an RDF statement to the target datasink.  Datasinks must
  implement grafter.rdf.protocols/ITripleWriteable.

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

  Takes an optional string/URI to use as a graph."
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

  The :format option is supplied by the wrapping function and may be
  nil, or act as an indicator about the format of the triples to read.
  Implementers can choose whether or not to ignore or require the
  format parameter.

  The :buffer-size option can be used to configure the buffer size at
  which statements are parsed from an RDF stream.  Its default value
  of 32 was found to work well in practice, and also aligns with chunk
  size of Clojure's lazy sequences."
  [this & {:keys [format buffer-size] :as options}]
  (pr/to-statements this options))
