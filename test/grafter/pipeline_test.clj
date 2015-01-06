(ns grafter.pipeline-test
  (:refer-clojure :exclude [ns-name])
  (:require [grafter.pipeline :refer :all]
            [clojure.test :refer :all]
            [clojure.java.io :as io])
  (:import [grafter.pipeline Pipeline]
           [java.io File]))


(deftest ns-name-test
  (is (= 'my.grafter.pipeline (ns-name '(ns my.grafter.pipeline
                                          (:require [foo.bar :as foo]))))))


(deftest form->Pipeline-test
  (let [namespace 'foo.bar
        ->Pipeline (partial ->Pipeline namespace)
        pipe-form->Pipeline (partial pipe-form->Pipeline namespace)]
    (testing "with defpipe"
      (is (= (->Pipeline 'my-pipeline
                         '[a b c]
                         "My Docstring"
                         {:meta true :doc "My Docstring"}
                         '((println "hello world"))
                         :pipe)

             (pipe-form->Pipeline
              '(defpipe my-pipeline
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
                           (println "world"))
                         :pipe)

             (pipe-form->Pipeline
              '(defpipe my-pipeline
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
                         '((println "hello world"))
                         :pipe)

             (pipe-form->Pipeline
              '(defpipe my-pipeline
                 "My Docstring"
                 [a b c]
                 (println "hello world"))))
          "Parses when no metadata is supplied")

      (is (= (->Pipeline 'my-pipeline
                         '[a b c]
                         nil
                         {}
                         '((println "hello world"))
                         :pipe)

             (pipe-form->Pipeline
              '(defpipe my-pipeline [a b c]
                 (println "hello world"))))
          "Parses when no metadata or docstring are supplied"))

    (testing "with defgraft"
      (is (= (->Pipeline 'my-graft
                         nil ; args
                         "My docstring"
                         nil ; meta
                         `(comp foo.bar/make-graph foo.bar/pipe)
                         :graft)

             (graft-form->Pipeline namespace '(defgraft my-graft "My docstring" foo.bar/pipe foo.bar/make-graph)))))))

(deftest find-pipelines-test
  (let [forms-seq '((if true "true" "false")
                    (defpipe invalid-pipeline)
                    (defpipe invalid 10)
                    (defpipe valid-pipeline [a b c])
                    (defgraft test-graft "test graft" valid-pipeline make-graph))
        [error another-error pipeline graft] (find-pipelines forms-seq)]

    (is (instance? Exception error))
    (is (instance? Exception another-error))
    (is (instance? grafter.pipeline.Pipeline pipeline))
    (is (= 'test-graft (:name graft)))
    (is (= "test graft" (:doc graft)))
    (is (= (:args pipeline) (:args graft))
        "Should inherit args from earlier pipeline definition")))

(defn write-forms-to [forms dest]
  (with-open [writer (io/writer dest)]
    (doseq [f forms]
      (.write writer (pr-str f)))))

(deftest find-resource-pipelines-test
  (let [pipeline-forms '((defpipe pfirst [a b c] (println "Hello world!"))
                         (defpipe psecond "docs" {:meta true} [d e] (println "Goodbye world!")))
        tmp (File/createTempFile "test" "pipelines.clj")]
    (try
      (write-forms-to pipeline-forms tmp)
      (let [tmp-url (.. tmp toURI toURL)
            read-pipeline-forms (find-resource-pipelines tmp-url)]
        (is (= (count pipeline-forms) (count read-pipeline-forms))))
      (finally
        (.delete tmp)))))
