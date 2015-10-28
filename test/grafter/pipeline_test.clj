(ns grafter.pipeline-test
  (:require [grafter.pipeline :refer :all]
            [clojure.test :refer :all]
            [grafter.tabular]
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
  )

(declare-pipeline url-pipeline-test
  "Test pipeline for map objects"
  [URL -> (Seq Statement)]
  {url "A URL"})



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
