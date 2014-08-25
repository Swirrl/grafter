(ns grafter.rdf.ontologies.pmd
  "Some convenience terms for the Publish My Data vocabulary."
  (:use [grafter.rdf.ontologies.util]))


(def pmd                 (prefixer "http://publishmydata.com/def/dataset#"))

(def pmd:Dataset         (pmd "Dataset"))

(def pmd:contactEmail    (pmd "contactEmail"))
(def pmd:graph           (pmd "graph"))


(def folder              (prefixer "http://publishmydata.com/def/ontology/folder/"))

(def folder:Folder       (folder "Folder"))

(def folder:hasTree      (folder "hasTree"))
(def folder:defaultTree  (folder "defaultTree"))
(def folder:parentFolder (folder "parentFolder"))
(def folder:inFolder     (folder "inFolder"))
(def folder:inTree       (folder "inTree"))
