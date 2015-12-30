(ns grafter.pipeline-test
  (:require [grafter.pipeline :refer :all]
            [clojure.test :refer :all]
            [grafter.tabular :refer [test-dataset]]
            [grafter.rdf]
            [schema.core :as s])
  (:import [java.net URI URL]))

(defn test-dataset-creator [rows cols]
  (grafter.tabular/test-dataset rows cols))

(declare-pipeline test-dataset-creator [Integer Integer -> Dataset]
  {rows "The number of rows of test data you want."
   cols "The number of columns of test data you want."})

(defn convert-persons-data-to-graphs
  [number-of-quads]
  (->> (range number-of-quads)
       (map #(grafter.rdf.protocols/->Quad (str "http://foo.bar/" %) "http://has-value/" %))))

(declare-pipeline convert-persons-data-to-graphs [Integer -> (Seq Statement)]
  {number-of-quads "The number of quads."})

(def PipelineSchema {s/Symbol {:name s/Symbol
                               :var clojure.lang.Var
                               (s/optional-key :display-name) s/Str
                               :doc s/Str
                               :args [{:name s/Symbol :class java.lang.Class :doc s/Str (s/optional-key :meta) {s/Keyword s/Any}}]
                               :type (s/either (s/eq :graft) (s/eq :pipe)) ;; one day maybe also :validation and a fallback of :function
                               :declared-args [s/Symbol]}
                     })

(deftest declare-pipeline-test
  (testing "declare-pipeline"
    (let [errors (s/check PipelineSchema
                          @exported-pipelines)]

      (testing "Creates pipelines that match our schema"
        (is (nil? errors)))

      (let [pipeline (@exported-pipelines 'grafter.pipeline-test/test-dataset-creator)]
        (is (= 'grafter.pipeline-test/test-dataset-creator (:name pipeline))
            "Is keyed by its :name")))))

(defn display-name-pipeline [an-argument]
  (grafter.tabular/test-dataset 2 2))

(declare-pipeline display-name-pipeline
  "Display Name Pipeline" [String -> Dataset]
  {an-argument "A string argument"})

(deftest declare-pipeline-with-display-name-test
  (let [pipeline (get @exported-pipelines 'grafter.pipeline-test/display-name-pipeline)]
    (is (= "Display Name Pipeline" (:display-name pipeline)))))

(defn map-pipeline-test [obj]
  [(grafter.rdf.protocols/->Quad "http://foo.bar/1" "http://has-value/" 1 "http://some-graph/")])

(declare-pipeline map-pipeline-test
  "Test pipeline for map objects"
  [Map -> (Seq Statement)]
  {obj "A map of key value pairs."})

(deftest declare-pipeline-with-test
  (let [pipeline (get @exported-pipelines 'grafter.pipeline-test/map-pipeline-test)]
    (is (= :graft (:type pipeline)))))

(defn uuid-pipeline-test [uuid]
  )

(declare-pipeline uuid-pipeline-test
  "Test pipeline for map objects"
  [UUID -> (Seq Statement)]
  {uuid "A UUID"})


(defn url-pipeline-test [url]
  [])

(declare-pipeline url-pipeline-test
  "Test pipeline for map objects"
  [URL -> (Seq Statement)]
  {url "A URL"})

(defn pipeline-string-argument-coercion [dataset string number bool hashmap
                                         uri url uuid keyword instant]
  (is (instance? incanter.core.Dataset dataset))
  (is (string? string))
  (is (number? number))
  (is (instance? Boolean bool))
  (is (map? hashmap))
  (is (instance? java.net.URI uri))
  (is (instance? java.net.URL url))
  (is (instance? java.util.UUID uuid))
  (is (keyword? keyword))
  (is (instance? java.util.Date instant))

  (test-dataset 1 1))

(declare-pipeline pipeline-string-argument-coercion
                  "Test pipeline coerces arguments properly"
                  [Dataset String Number Boolean Map URI URL UUID clojure.lang.Keyword java.util.Date -> Dataset]
                  {dataset "A Dataset"
                   string "a String"
                   number "a Number"
                   bool "a boolean"
                   hashmap "A hashmap"
                   uri "A URI"
                   url "A URL"
                   uuid "A UUID"
                   keyword "A keyword"
                   instant "A date instant"
                   })

(deftest coerce-pipeline-arguments-test
  (apply pipeline-string-argument-coercion (coerce-pipeline-arguments 'grafter.pipeline-test/pipeline-string-argument-coercion
                                                                      ["./test/grafter/test.csv"
                                                                       "a string"
                                                                       "10"
                                                                       "true"
                                                                       "{ \"foo\" \"bar\"}"
                                                                       "http://localhost/a/uri"
                                                                       "http://localhost/a/url"
                                                                       "cabec818-df6a-4c27-b445-117163e70227"
                                                                       ":foo"
                                                                       "2015"])))


(deftest execute-pipeline-with-coerced-arguments-test
  (is (= (test-dataset 1 1)
         (execute-pipeline-with-coerced-arguments 'grafter.pipeline-test/pipeline-string-argument-coercion
                                                  ["./test/grafter/test.csv"
                                                   "a string"
                                                   "10"
                                                   "true"
                                                   "{ \"foo\" \"bar\"}"
                                                   "http://localhost/a/uri"
                                                   "http://localhost/a/url"
                                                   "cabec818-df6a-4c27-b445-117163e70227"
                                                   ":foo"
                                                   "2015"]))))
