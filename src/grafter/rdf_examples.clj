(ns grafter.rdf-examples
  (:use [grafter.rdf]
        [grafter.rdf.sesame])
  (:require [grafter.rdf.protocols :as pr]))

(def my-repo (-> "./tmp/grafter-sesame-store" native-store repo))

(defn add-triples [repo]
  (pr/add repo (expand-subject ["http://test.org/bob"
                                ["http://is/a" "http://class/Person"]
                                ["http://rdfs/label" (s "Bob Jones")]
                                ["http://date-of-birth/" #inst "1980-01-02"]])))
