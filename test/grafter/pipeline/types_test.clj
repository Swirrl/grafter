(ns grafter.pipeline.types-test
  (:require [grafter.pipeline.types :as sut]
            [clojure.test :as t])
  (:import java.net.URI))

(t/deftest parse-parameter-test
  (t/testing "::primitive values"
    (t/are [type str-val coerced-val]
        (let [parsed-param (sut/parse-parameter type str-val {})]
          (t/is (= coerced-val parsed-param))
          (t/is (= type (class coerced-val))))


      Boolean "true" true
      Boolean "nil" false

      Integer "1234" (int 1234)
      Float "2.0" (float 2.0)
      Double "3.3" 3.3
      Long "1234" 1234
      clojure.lang.BigInt "1234" (bigint 1234)

      String "Hello world" "Hello world"
      clojure.lang.Keyword "coerced-into-a-keyword" :coerced-into-a-keyword

      URI "http://example.com/test" (URI. "http://example.com/test")))

  (t/testing "::edn-primitive hierarchy"
    (t/are [target-type str-val coerced-type msg]
        (t/is (isa? @sut/parameter-types
                    (type (sut/parse-parameter target-type str-val {}))
                    coerced-type)
              msg)


      ::sut/url "http://example.com/test" URI
      "::sut/url coerces to a java.net.URI")))

(swap! sut/parameter-types derive ::my-new-data-type ::sut/primitive)

(swap! sut/parameter-types derive ::my-new-sub-type ::my-new-data-type)

(t/deftest parameter-type-chain-test
  (t/testing "parameter-type-chain Reports hierarchy ordered from sub-type to super-type"
    (t/is (= [::my-new-sub-type
              ::my-new-data-type
              :grafter.pipeline.types/primitive]
             (sut/parameter-type-chain ::my-new-sub-type)))))


;; Records implement java.util.Map, so can cause a problem in
;; inheritance hierarchy that needs to be resolve with
;; clojure.core/prefer-method.  Create this in the tests to show how
;; parameter-type-chain obeys prefer-method too.

(defrecord TestRecordType [])

(defmethod sut/parse-parameter [String TestRecordType] [_ val opts]
  ::incanter-dataset-method)

(defmethod sut/parse-parameter [String java.util.Map] [_ val opts]
  ::map-method)

(prefer-method sut/parse-parameter grafter.pipeline.types_test.TestRecordType java.util.Map)

(t/deftest parameter-type-chain-prefer-method
  (t/testing "Uses clojure.core/prefer-method to resolve multiple-inheritance conflicts in ordering parameter-type-chain"
    (t/is (= [grafter.pipeline.types_test.TestRecordType java.util.Map]
             (sut/parameter-type-chain grafter.pipeline.types_test.TestRecordType)))))
