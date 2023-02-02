(ns grafter-2.rdf4j.templater-test
  (:require [grafter-2.rdf4j.templater :as sut]
            [clojure.test :as t]
            [grafter-2.rdf.protocols :as pr]
            [grafter.url :as url]))

(def hasBnode (url/->java-uri "http://example.org/hasBlankNode"))
(def p (url/->java-uri "http://example.org/test/predicate"))
(def o (url/->java-uri "http://example.org/test/object"))

(t/deftest triplify-with-bnode
  (let [triples (sut/triplify [(url/->java-uri "http://example.org/test/subject")
                               [hasBnode [[p o]]]])]

    (t/is (= 2 (count triples)))

    (let [main-res (first triples)
          bnode-res (second triples)]
      (t/is (= hasBnode (:p main-res)))

      (t/testing "BNode object of main-res is subject of bnode triple"
        (t/is (pr/blank-node? (:o main-res)))
        (t/is (pr/blank-node? (:s bnode-res)))
        (t/is (= p (:p bnode-res)))
        (t/is (= o (:o bnode-res)))))))
