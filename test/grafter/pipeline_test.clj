(ns grafter.pipeline-test
  (:require [grafter.pipeline :refer :all]
            [clojure.test :refer :all]
            [grafter.tabular]
            [grafter.rdf]
            [schema.core :as s]))

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
                               :doc s/Str
                               :args [{:name s/Symbol :class java.lang.Class :doc s/Str}]
                               :type s/Keyword
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

(comment
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
          (.delete tmp))))))
