(ns grafter.rdf.ontologies.pmd
  "Some convenience terms for the Publish My Data vocabulary."
  (:require [grafter.rdf.ontologies.util :refer :all]))


(def pmd                 (prefixer "http://publishmydata.com/def/dataset#"))

(def pmd:Dataset         (pmd "Dataset"))
(def pmd:LinkedDataset   (pmd "LinkedDataset"))
(def pmd:FileDataset     (pmd "FileDataset"))
(def pmd:DeprecatedDataset (pmd "DeprecatedDataset"))

(def pmd:contactEmail    (pmd "contactEmail"))
(def pmd:graph           (pmd "graph"))

(def pmd:fileName (pmd "fileName"))
(def pmd:fileExtension (pmd "fileExtension"))
(def pmd:mediaType (pmd "mediaType"))
(def pmd:sizeInBytes (pmd "sizeInBytes"))
(def pmd:downloadURL (pmd "downloadURL"))


(def folder              (prefixer "http://publishmydata.com/def/ontology/folder/"))

(def folder:Folder       (folder "Folder"))

(def folder:hasTree      (folder "hasTree"))
(def folder:defaultTree  (folder "defaultTree"))
(def folder:parentFolder (folder "parentFolder"))
(def folder:inFolder     (folder "inFolder"))
(def folder:inTree       (folder "inTree"))
