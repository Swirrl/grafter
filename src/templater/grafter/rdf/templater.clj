(ns grafter.rdf.templater
  "Functions for converting tree's of turtle-like data into Linked
  Data statements (triples/quads)."
  (:require [grafter.rdf :as rdf])
  (:require [grafter.rdf.protocols :refer [->Triple ->Quad]])
  (:import [org.openrdf.rio RDFFormat]
           [org.openrdf.model URI]))

(defn- valid-uri? [node]
  (let [types [java.lang.String java.net.URL java.net.URI URI]]
    (some (fn [t] (instance? t node)) types)))

(defn- is-literal? [node]
  (not (or (nil? node)
           (vector? node)
           (map? node)
           (set? node))))

(defn- valid-subject? [node]
  (or (valid-uri? node)
      (keyword? node)))

(defn- valid-predicate? [node]
  (valid-uri? node))

(defn- valid-object? [object]
  (if (or (valid-subject? object)
          (is-literal? object))
    true
    (when (vector? object)
      (cond
       (= 2 (count object)) (let [[p o] object]
                              (and (valid-predicate? p)
                                   (valid-object? o)))
       (= 1 (count object)) (valid-object? (first object))))))

(defn- blank-node? [node]
  (vector? node))

(defn- make-triples [subject predicate object-or-nested-subject]
  (if (blank-node? object-or-nested-subject)
    (let [bnode-resource (keyword (gensym "bnode"))
          nested-pairs object-or-nested-subject]

      (-> (mapcat (partial make-triples bnode-resource)
                  (map first nested-pairs)
                  (map second nested-pairs))
          (conj (->Triple subject predicate bnode-resource))))

    (let [object object-or-nested-subject]
      [(->Triple subject predicate object)])))

(defn- expand-subj
  "Takes a turtle like data structure, like that passed to graph and
  converts it to triples."
  [[subject & po-pairs]]

  (mapcat (fn [[predicate object]]
            (make-triples subject predicate object)) po-pairs))

(defn triplify
  "Takes many turtle like structures and converts them to a lazy-seq
  of grafter.rdf.protocols.IStatement's.  Users should generally tend
  to prefer to using graph to triplify."
  [& subjects]
  (mapcat expand-subj subjects))

(defn- quad
  "Build a quad from a graph and a grafter.rdf.protocols/Triple."
  [graph triple]
  (->Quad (rdf/subject triple)
          (rdf/predicate triple)
          (rdf/object triple)
          graph))

(defn graph
  "Takes a graph-uri and a turtle-like template of vectors and returns
  a lazy-sequence of quad Statements.  A turtle-like template should
  be structured like this:

  ````
  [subject [predicate1 object1]
           [predicate2 object2]
           [predicate3 [[blank-node-predicate blank-node-object]]]]
  ````

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
