(ns grafter-2.rdf.protocols-test
  (:require [grafter.vocabularies.xsd :refer [xsd:boolean xsd:string]]
            [grafter-2.rdf.protocols :as pr]
            [cljs.test :refer (deftest testing is are)]))

(deftest langstring-test
  (testing "langstring"
    (let [lang-string (pr/->LangString "foo-bar" :en)]
      (is (= "foo-bar" (pr/raw-value lang-string)))
      (is (instance? pr/LangString lang-string)))))

(deftest literal-test
  (testing "literal"
    (let [lit (pr/literal "foo" "http://www.w3.org/2001/XMLSchema#string")]
      (are [x y] (= x y)
                 "foo" (pr/raw-value lit)
                 xsd:string (pr/datatype-uri lit)))))

(deftest quad-test
  (testing "Quad indexing"
    (let [q (pr/->Quad "http://subject/" "http://predicate/" "http://object/" "http://context/")]
      (are [x y] (= x y)
                 "http://subject/" (nth q 0)
                 "http://predicate/" (nth q 1)
                 "http://object/" (nth q 2)
                 "http://context/" (nth q 3)))))

(deftest datatype-uri-test
  (testing "datatype-uri"
    (are [x y] (= x y)
      xsd:boolean (pr/datatype-uri true)
      xsd:boolean (pr/datatype-uri false)

      xsd:string (pr/datatype-uri "")
      xsd:string (pr/datatype-uri " ")
      xsd:string (pr/datatype-uri "a")
      xsd:string (pr/datatype-uri "a b"))))

(deftest bnode-equality-test
  (testing "BNode equality"
    (let [id (gensym)]
      (is (= (pr/make-blank-node id)
             (pr/make-blank-node id))))))

(deftest triple=-test
  (testing "triple= quads"
    (is (pr/triple= (pr/->Quad "http://subject/" "http://predicate/" "http://object/" "http://context/")
                    (pr/->Quad "http://subject/" "http://predicate/" "http://object/" nil)
                    (pr/->Quad "http://subject/" "http://predicate/" "http://object/" "http://context/2"))))

  (testing "not triple="
    (pr/triple= (pr/->Quad "http://subject/1" "http://predicate/" "http://object/" "http://context/")
                (pr/->Quad "http://subject/2" "http://predicate/" "http://object/" "http://context/"))))
