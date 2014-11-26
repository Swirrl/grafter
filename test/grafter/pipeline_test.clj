(ns grafter.pipeline-test
  (:refer-clojure :exclude [ns-name])
  (:require [grafter.pipeline :refer :all]
            [clojure.test :refer :all])
  (:import [grafter.pipeline Pipeline]))


(deftest ns-name-test
  (is (= 'my.grafter.pipeline (ns-name '(ns my.grafter.pipeline
                                          (:require [foo.bar :as foo]))))))


(deftest form->Pipeline-test
  (let [namespace 'foo.bar
        ->Pipeline (partial ->Pipeline namespace)
        form->Pipeline (partial form->Pipeline namespace)]

    (is (= (->Pipeline 'my-pipeline
                       '[a b c]
                       "My Docstring"
                       {:meta true :doc "My Docstring"}
                       '((println "hello world")))

           (form->Pipeline
            '(defpipeline my-pipeline
               "My Docstring"
               {:meta true}
               [a b c]
               (println "hello world"))))

        "Parses fully specified pipeline (function) definition")

    (is (= (->Pipeline 'my-pipeline
                       '[a b c]
                       "My Docstring"
                       {:meta true :doc "My Docstring"}
                       '((println "hello")
                         (println "world")))

           (form->Pipeline
            '(defpipeline my-pipeline
               "My Docstring"
               {:meta true}
               [a b c]
               (println "hello")
               (println "world"))))
        "Parses implicit do in body")

    (is (= (->Pipeline 'my-pipeline
                       '[a b c]
                       "My Docstring"
                       {:doc "My Docstring"}
                       '((println "hello world")))

           (form->Pipeline
            '(defpipeline my-pipeline
               "My Docstring"
               [a b c]
               (println "hello world"))))
        "Parses when no metadata is supplied")

    (is (= (->Pipeline 'my-pipeline
                       '[a b c]
                       nil
                       {}
                       '((println "hello world")))

           (form->Pipeline
            '(defpipeline my-pipeline [a b c]
               (println "hello world"))))
        "Parses when no metadata or docstring are supplied")))

(deftest find-pipelines-test
  (let [forms-seq '((if true "true" "false")
                    (defpipeline invalid-pipeline)
                    (defpipeline invalid 10)
                    (defpipeline valid-pipeline [a b c]))
        [error another-error pipeline] (find-pipelines forms-seq)]

    (is (instance? Exception error))
    (is (instance? Exception another-error))
    (is (instance? Pipeline pipeline))))
