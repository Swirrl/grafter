(ns grafter.rdf.ontologies.pmd
  (:use [grafter.rdf.ontologies.util]))

(def pmd (prefixer "http://publishmydata.com/def/dataset#"))


(def pmd:contactEmail (pmd "contactEmail"))
(def pmd:graph (pmd "graph"))

(def pmd:Dataset (pmd "Dataset"))

(defn dataset [dataset-slug date title label comment description email]
  (let [dataset-uri (str (base-uri "/data") dataset-slug)
        data-graph (str (base-graph dataset-slug))
        metadata-graph (str data-graph "/metadata")]

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
