(ns grafter.pipeline-test-two
  (:require [grafter.pipeline-two :refer :all]
            [clojure.test :refer :all]
            [grafter.tabular :refer [test-dataset]]
            [grafter.rdf]
            [schema.core :as s])
  (:import [java.net URI URL]
           [java.util Map UUID]
           [incanter.core Dataset]))

(defn test-default-types [bool int biginteger dbl flt uri date kwd uuid]
  (is (boolean? bool))
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
  [Boolean Integer clojure.lang.BigInt Double Float :grafter.pipeline.types-two/uri java.util.Date clojure.lang.Keyword java.util.UUID -> Quads]
  {bool "A boolean value"
   int "An integer"
   biginteger "A bigint"
   dbl "A double"
   flt "A float"
   uri "A URI"
   date "A date"
   kwd "A keyword"
   uuid "A UUID"
   })

(deftest declare-pipeline-test
  (execute-pipeline-with-coerced-arguments 'grafter.pipeline-test-two/test-default-types
                                           ["true" "123" "9999999999999999999999999"
                                            "2.3" "3.0"
                                            "http://foo"
                                            "2015"
                                            ":a-keyword"
                                            "04eccc7e-bddd-44e5-a299-8879512a3ceb"])
  )
