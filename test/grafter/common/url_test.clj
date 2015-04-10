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
      ->url))

(deftest test-url-builders
  (let [in "http://foobar.com/blah/blah/blah"
        expected (URL. "https://yak-hair.com:9000/articles/yaks/hair/shaving?article-id=1#11-things-you-can-do-with-yak-hair")]

    (are [expected test] (= (str expected) (str (build-url test)))

         expected (url-builder in)
         expected (URL. in)
         expected (URI. in))))
