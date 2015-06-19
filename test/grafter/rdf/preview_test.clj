(ns grafter.rdf.preview-test
  (:require [grafter.rdf.preview :refer :all]
            [clojure.test :refer :all]
            [grafter.rdf.templater :refer [graph]]
            [grafter.tabular :refer [make-dataset graph-fn]]
            [schema.core :as s]
            [grafter.rdf :as rdf]))

(def rdf:a "rdf:a")
(def foaf:name "foaf:name")
(def foaf:age "foaf:age")
(def foaf:Person "foaf:Person")
(def foaf:knows "foaf:knows")

(def test-data (make-dataset [["http://graph/" "http://bob/" "Bob" 35 "http://alice/" "Alice" 30]
                              ["http://graph/" "http://wayne/" "Wayne" 24 "http://jane/" "Jane" 20]]
                             ["persons-graph-uri" "person-uri" "person-name" "person-age" "friend-uri" "friend-name" "friend-age"]))

;; A standard graph-fn graph template
(def test-template (graph-fn [{:strs [persons-graph-uri person-uri person-name person-age friend-uri friend-name friend-age]}]
                             (graph persons-graph-uri
                                    [person-uri
                                     [rdf:a foaf:Person]
                                     [foaf:name person-name]
                                     [foaf:age person-age]
                                     [foaf:knows friend-uri]]
                                    [friend-uri
                                     [rdf:a foaf:Person]
                                     [foaf:name friend-name]
                                     [foaf:age friend-age]
                                     [foaf:knows person-uri]])))

(deftest preview-graph-test
  (let [schema {:bindings {:strs [s/Symbol]}
                :row s/Any
                :template [s/Any]}]

    (testing "Substitutes data into template"
      (let [preview (preview-graph test-data test-template 0)]

        (is (s/validate schema preview)
            "Outer most object conforms to Schema")

        (let [template (first (:template preview))]
          (is (= '(graph "http://graph/"
                         ["http://bob/"
                          [rdf:a foaf:Person]
                          [foaf:name "Bob"]
                          [foaf:age 35]
                          [foaf:knows "http://alice/"]]
                         ["http://alice/"
                          [rdf:a foaf:Person]
                          [foaf:name "Alice"]
                          [foaf:age 30]
                          [foaf:knows "http://bob/"]]) template)

              "Template includes substitutions from the source data"))))

    (testing "Substitutes renderable constants and data into template"
      (let [preview (preview-graph test-data test-template 1 :render-constants)]

        (is (s/validate schema preview)
            "Outer most object conforms to Schema")

        (let [template (first (:template preview))]
          (is (= '(graph "http://graph/"
                         ["http://wayne/"
                          ["rdf:a" "foaf:Person"]
                          ["foaf:name" "Wayne"]
                          ["foaf:age" 24]
                          ["foaf:knows" "http://jane/"]]
                         ["http://jane/"
                          ["rdf:a" "foaf:Person"]
                          ["foaf:name" "Jane"]
                          ["foaf:age" 20]
                          ["foaf:knows" "http://wayne/"]]) template)

              "Template includes substitutions from the source data"))))))

(def unprintable-template (graph-fn [{:strs [a b c g]}]
                                    (graph g
                                           [a [b c]])))

(deftest unprintable-previews-test
  (let [obj (Object.)
        ds (make-dataset [["a" "b" obj]])
        template (:template (preview-graph ds unprintable-template 0))
        unprintable-item (second (second (nth (first template) 2)))]

    (is (instance? grafter.rdf.preview.UnreadableForm unprintable-item)
        "Unprintable item should be wrapped in a printable wrapper.")

    (is (= (str obj) (:form-string unprintable-item)))
    (is (= (.getName (class obj))
           (:form-class unprintable-item)))))
