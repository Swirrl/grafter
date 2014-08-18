(ns grafter.rdf
  (:use
   [grafter.rdf.ontologies.rdf]
   [grafter.rdf.ontologies.void]
   [grafter.rdf.ontologies.dcterms]
   [grafter.rdf.ontologies.vcard]
   [grafter.rdf.ontologies.pmd]
   [grafter.rdf.ontologies.qb]
   [grafter.rdf.ontologies.os]
   [grafter.rdf.ontologies.skos]
   [grafter.rdf.ontologies.owl]
   [grafter.rdf.ontologies.sdmx-measure]
   [grafter.rdf.ontologies.sdmx-attribute]
   [grafter.rdf.ontologies.sdmx-concept])
  (:require [clojure.java.io :as io]
            [grafter.rdf.protocols :as pr]
            [incanter.core :as inc]
            [grafter.rdf.sesame :as ses]
            [grafter.tabular :refer [make-dataset column-names]])
  (:import [grafter.rdf.protocols Triple Quad]
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

(def ^{:doc "Coerce a string into an RDF string"} s ses/s)

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

(defn quad
  ([graph triple]
     (Quad. (pr/subject triple)
            (pr/predicate triple)
            (pr/object triple)
            graph)))

(defn- expand-subj
  "Takes a turtle like data structure and converts it to triples e.g.

   [:rick [:a :Person]
          [:age 34]]"
  [[subject & po-pairs]]

  (mapcat (fn [[predicate object]]
            (make-triples subject predicate object)) po-pairs))

(defn triplify [& subjects]
  "Takes many turtle like structures and converts them to a lazy-seq
of grafter.rdf.protocols.IStatement's"
  (mapcat expand-subj subjects))

(defn graph [graph-uri & triples]
  (map (partial quad graph-uri)
       (apply triplify triples)))

(defn add-properties [triple-template hash-map]
  "Appends the key/value pairs from the supplied hash-map into the
  triple-template form.  Assumes it is given a vector representing a
  single subject."
  (reduce conj triple-template
          (mapcat vector hash-map)))

(defn canonicalise-column-names
  [dataset]
  (make-dataset dataset (map name (column-names dataset))))

(defmacro graph-fn
  "Takes FIXME followed by a series of graph or triplify forms and concatenates them
  all together."
  [row-bindings & forms]
  (let [row-sym (gensym "row")
        row-bindings (conj row-bindings :as row-sym)
        ]
    `(fn graphify-dataset [ds#]
       (mapcat (fn graphify-row [~row-bindings]
                 (->> (concat ~@forms)
                      (map (fn [triple#]
                             (with-meta triple# {:row ~row-sym}))))) (inc/to-list ds#)))))

(defn load-triples [my-repo triple-seq]
  (doseq [triple triple-seq]
    (try
      (pr/add-statement my-repo triple)
      (catch java.lang.IllegalArgumentException e
        (throw (Exception.
                (str "Problem loading triple: " (print-str triple) " from row: " (-> triple meta :row)) e)))))
  my-repo)

(def prefixer ontutils/prefixer)

(defn dataset [dataset-uri data-graph date title label comment description email]
  (let [metadata-graph (str data-graph "/metadata")]

    (graph metadata-graph
           [dataset-uri
            [rdf:a pmd:Dataset]
            [rdfs:comment (s comment :en)]
            [rdfs:label (s label :en)]
            [dcterms:description (s description :en)]
            [pmd:contactEmail email]
            [pmd:graph data-graph]
            [dcterms:issued date]
            [dcterms:modified date]])))
