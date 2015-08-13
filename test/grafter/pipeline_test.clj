(ns grafter.pipeline-test
  (:require [grafter.pipeline :refer :all]
            [clojure.test :refer :all]))

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
