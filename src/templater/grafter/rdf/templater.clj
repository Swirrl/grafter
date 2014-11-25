(ns grafter.rdf.templater
  (:require [grafter.rdf.protocols :refer [->Triple quad]]))

(defn- make-triples [subject predicate object-or-nested-subject]
  (if (vector? object-or-nested-subject)
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
