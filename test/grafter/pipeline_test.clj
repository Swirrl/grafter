(ns grafter.pipeline-test
  (:require [grafter.pipeline :refer :all]
            [clojure.test :refer :all]
            [grafter.pipeline.types :as types]
            [grafter.tabular :refer [test-dataset]]
            [grafter.rdf]
            [schema.core :as s])
  (:import [java.net URI URL]
           [java.util Map UUID]
           [incanter.core Dataset]))

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

(def ArgumentType (s/either java.lang.Class s/Keyword))

(def PipelineSchema {s/Symbol {:name s/Symbol
                               :var clojure.lang.Var
                               (s/optional-key :display-name) s/Str
                               :namespace s/Symbol
                               :doc s/Str
                               :args [{:name s/Symbol :class ArgumentType :doc s/Str (s/optional-key :meta) {s/Keyword s/Any}}]
                               :type (s/either (s/eq :graft) (s/eq :pipe)) ;; one day maybe also :validation and a fallback of :function
                               :declared-args [s/Symbol]
                               :supported-operations #{(s/enum :append :delete)}}
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

(def a-quad (grafter.rdf.protocols/->Quad "http://foo.bar/1" "http://has-value/" 1 "http://some-graph/"))

(defn map-pipeline-test [obj]
  [a-quad])

(declare-pipeline map-pipeline-test
  "Test pipeline for map objects"
  [:grafter.pipeline.types/map -> Quads]
  {obj "A map of key value pairs."})

(defn quads-pipeline []
  [a-quad])

(declare-pipeline quads-pipeline
  "Test pipeline for map objects"
  [-> Quads]
  {})

(defn seq-quad-pipeline []
  [a-quad])

(declare-pipeline seq-quad-pipeline
  "Test pipeline for map objects"
  [-> Quads]
  {})

(deftest declare-pipeline-with-test
  (are [pipeline-name]
      (let [pipeline (get @exported-pipelines pipeline-name)]
        (is (= :graft (:type pipeline))))

    'grafter.pipeline-test/map-pipeline-test
    'grafter.pipeline-test/quads-pipeline
    'grafter.pipeline-test/seq-quad-pipeline))

(defn uuid-pipeline-test [uuid])

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
  [Dataset String Integer Boolean ::types/map  URI URL UUID clojure.lang.Keyword java.util.Date -> Dataset]
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
                                                                      ["./dev/resources/grafter/tabular/test.csv"
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
                                                  ["./dev/resources/grafter/tabular/test.csv"
                                                   "a string"
                                                   "10"
                                                   "true"
                                                   "{ \"foo\" \"bar\"}"
                                                   "http://localhost/a/uri"
                                                   "http://localhost/a/url"
                                                   "cabec818-df6a-4c27-b445-117163e70227"
                                                   ":foo"
                                                   "2015"]))))

(defn delete-only-pipeline [uri])
(defn append-only-pipeline [uri])

(declare-pipeline delete-only-pipeline
                  [URI -> (Seq Statement)]
                  {uri "URI"}
                  :supported-operations #{:delete})

(declare-pipeline append-only-pipeline
                  [URI -> (Seq Statement)]
                  {uri "URI"})

(deftest supported-operations-test
  (are [pipeline-sym expected] (= expected (get-in @exported-pipelines [pipeline-sym :supported-operations]))
                               'grafter.pipeline-test/delete-only-pipeline #{:delete}
                               'grafter.pipeline-test/append-only-pipeline #{:append}))

(defn test-default-types [bool lng int biginteger dbl flt uri date kwd uuid]
  (is (boolean? bool))
  (is (instance? Long lng))
  (is (integer? int))
  (is (instance? clojure.lang.BigInt biginteger))
  (is (double? dbl))
  (is (float? flt))
  (is (instance? java.net.URI uri))
  (is (instance? java.util.Date date))
  (is (keyword? kwd))
  (is (instance? java.util.UUID uuid))

  [bool int biginteger dbl flt uri date kwd uuid])

(declare-pipeline test-default-types "Test pipeline"
  [Boolean Long Integer clojure.lang.BigInt Double Float :grafter.pipeline.types/uri java.util.Date clojure.lang.Keyword java.util.UUID -> Quads]
  {bool "A boolean value"
   lng "A long"
   int "An integer"
   biginteger "A bigint"
   dbl "A double"
   flt "A float"
   uri "A URI"
   date "A date"
   kwd "A keyword"
   uuid "A UUID"
   })

(deftest declare-pipeline-test-2
  (execute-pipeline-with-coerced-arguments 'grafter.pipeline-test/test-default-types
                                           ["true"
                                            "123"
                                            "123456789"
                                            "9999999999999999999999999"
                                            "2.3"
                                            "3.0"
                                            "http://foo"
                                            "2015"
                                            ":a-keyword"
                                            "04eccc7e-bddd-44e5-a299-8879512a3ceb"]))

(defn symbol-deref-test-pipeline [klass kwd]
  [klass kwd])

(def klass-symbol URI)

(def keyword-symbol ::types/primitive)

(def not-a-class-or-a-keyword "an unsupported type")

(deftest declare-pipeline-dereferencing-test
  (testing "declare-pipeline resolves classes or keywords from vars"
    (is (nil? (eval `(declare-pipeline symbol-deref-test-pipeline
                       [klass-symbol keyword-symbol ~'-> ~'(Seq Statement)]
                       {~'klass "URI"
                        ~'kwd "Keyword"}))))))


(deftest declare-pipeline-dereferencing-test-2
  (testing "declare-pipeline errors if vars don't contain a valid type (either class or keyword)"
    (is (thrown? IllegalArgumentException
                 (eval `(declare-pipeline symbol-deref-test-pipeline
                          [not-a-class-or-a-keyword keyword-symbol ~'-> ~'(Seq Statement)]
                          {~'klass "A class"
                           ~'kwd "A keyword"}
                          :supported-operations #{:delete})))
        "Raises an exception because the string in not-a-class-or-a-keyword is not a valid parameter type")))
