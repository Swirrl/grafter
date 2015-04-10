(ns grafter.common.url-test
  (:require [clojure.test :refer :all]
            [grafter.common.url :refer :all])
  (:import [java.net URL URI]))

(defn build-url [base]
  (-> base
      (set-host "yak-hair.com")
      (set-port 9000)
      (set-scheme "https")
      (set-url-fragment "11-things-you-can-do-with-yak-hair")
      (set-path-segments ["articles" "yaks"])
      (append-path-segments ["hair" "shaving"])
      (set-query-params {"article-id" 1})
      str))

(deftest test-url-builders
  (let [in "http://foobar.com/blah/blah/blah"
        expected (URL. "https://yak-hair.com:9000/articles/yaks/hair/shaving?article-id=1#11-things-you-can-do-with-yak-hair")]

    (are [expected test] (is (= (str expected) (str (build-url test))))

         expected (url-builder in)
         expected (URL. in)
         expected (URI. in))))

(deftest test-protocol

  (let [bar "http://bar.com/"]
    (are [value getter setter] (do (is (= value
                                          (-> (URI. bar)
                                              (setter value)
                                              getter)))

                                   (is (= value
                                          (-> (URL. bar)
                                              (setter value)
                                              getter)))

                                   (is (= value
                                          (-> (url-builder bar)
                                              (setter value)
                                              getter))))
         ;; test that what goes in comes out for all three
         ;; implementations.
         "foo.com" host set-host
         "https" scheme set-scheme
         nil port set-port
         9000 port set-port
         "foo" url-fragment set-url-fragment
         ["foo" "bar"] path-segments set-path-segments
         {"foo" "bar" "baz" "fop"} query-params-map set-query-params

         ;; TODO test query-params elsewhere as its not dual to set-query-params
         )))
