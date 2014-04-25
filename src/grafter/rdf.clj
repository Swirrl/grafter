(ns grafter.rdf
  (:require [clojure.java.io :as io])
  (:require [grafter.rdf.protocols :as pr])
  (:require [grafter.rdf.sesame :as ses])
  (:import [grafter.rdf.protocols Triple]
           [grafter.rdf.sesame ISesameRDFConverter])
  (:import [org.openrdf.model Statement Value Resource Literal URI BNode ValueFactory]
           [org.openrdf.model.impl ValueFactoryImpl LiteralImpl]
           [org.openrdf.repository Repository]
           [org.openrdf.repository.sail SailRepository]
           [org.openrdf.sail.memory MemoryStore]
           [org.openrdf.sail.nativerdf NativeStore]
           [org.openrdf.query TupleQuery TupleQueryResult BindingSet QueryLanguage]
           [org.openrdf.rio RDFFormat])
  (:require [grafter.rdf.ontologies.util :as ontutils]))

(def format-rdf-xml RDFFormat/RDFXML)
(def format-rdf-n3 RDFFormat/N3)
(def format-rdf-ntriples RDFFormat/NTRIPLES)
(def format-rdf-nquads RDFFormat/NQUADS)
(def format-rdf-turtle RDFFormat/TURTLE)
(def format-rdf-jsonld RDFFormat/JSONLD)
(def format-rdf-trix RDFFormat/TRIX)
(def format-rdf-trig RDFFormat/TRIG)

(defn s
  "Cast a string to an RDF literal"
  ([str]
     {:pre [(string? str)]}
     (reify Object
       (toString [_] str)
       ses/ISesameRDFConverter
       (ses/->sesame-rdf-type [this]
         (LiteralImpl. str))))
  ([str lang-or-uri]
     {:pre [(string? str) (or (string? lang-or-uri) (keyword? lang-or-uri) (instance? URI lang-or-uri))]}
     (reify Object
       (toString [_] str)
       ses/ISesameRDFConverter
       (ses/->sesame-rdf-type [this]
         (LiteralImpl.  str (if (keyword? lang-or-uri)
                              (name lang-or-uri)
                              lang-or-uri))))))

(defn- make-triples [subject predicate object-or-nested-subject]
  (if (vector? object-or-nested-subject)
    (let [bnode-resource (keyword (gensym "bnode"))
          nested-pairs object-or-nested-subject]
      (-> (mapcat (partial make-triples bnode-resource)
                  (map first nested-pairs)
                  (map second nested-pairs))
          (conj (Triple. subject predicate bnode-resource))))
    (let [object object-or-nested-subject]
      [(Triple. subject predicate object)])))

(defn- expand-subj
  "Takes a turtle like data structure and converts it to triples e.g.

   [:rick [:a :Person]
          [:age 34]]"
  [[subject & po-pairs]]

  (mapcat (fn [[predicate object]]
            (make-triples subject predicate object)) po-pairs))


(comment (defn- expand-subj
           "Takes a turtle like data structure and converts it to triples e.g.

   [:rick [:a :Person]
          [:age 34]]"
           [[subject & po-pairs]]
           (mapcat (partial make-triples subject) po-pairs)))


(defn triplify [& subjects]
  "Takes many turtle like structures and converts them to a lazy-seq
of grafter.rdf.protocols.IStatement's"
  (mapcat expand-subj subjects))

(def prefixer ontutils/prefixer)
