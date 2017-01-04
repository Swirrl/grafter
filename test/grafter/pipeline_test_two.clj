(ns grafter.pipeline-test-two
  (:require [grafter.pipeline :refer :all]
            [clojure.test :refer :all]
            [grafter.tabular :refer [test-dataset]]
            [grafter.rdf]
            [schema.core :as s])
  (:import [java.net URI URL]
           [java.util Map UUID]
           [incanter.core Dataset]))



(deftest declare-pipeline-test)
