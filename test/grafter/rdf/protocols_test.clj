(ns grafter.rdf.protocols-test
  (:require [clojure.test :refer :all]
            [grafter.rdf.protocols :refer :all]
            [grafter.rdf :refer [language]]
            [grafter.vocabularies.xsd :refer :all]))

(def test-data [["http://a1" "http://b1" "http://c1" "http://graph1"]
                ["http://a2" "http://b2" "http://c2" "http://graph2"]])

(def first-quad (->Quad "http://a1" "http://b1" "http://c1" "http://graph1"))

(def second-quad (->Quad "http://a2" "http://b2" "http://c2" "http://graph2"))

(deftest quads-test
  (testing "Quads"
    (testing "support positional destructuring"
      (let [quad (->Quad "http://subject/" "http://predicate/" "http://object/" "http://context/")
            [s p o c] quad]

        (is (= "http://subject/" s))
        (is (= "http://predicate/" p))
        (is (= "http://object/" o))
        (is (= "http://context/" c))))))

(deftest rdf-strings-test
  (testing "RDF Strings"
    (let [en (language "Hello" :en)
          sesame-fr (org.openrdf.model.impl.LiteralImpl. "Bonjour" "fr")
          sesame-nolang (org.openrdf.model.impl.LiteralImpl. "Bonjour")]
      (are [expected test-val]
          (is (= expected test-val))

        :en (lang en)
        "Hello" (raw-value en)
        "Hello" (str en)
        rdf:langString (datatype-uri en)

        :fr (lang sesame-fr)
        "Bonjour" (raw-value sesame-fr)

        ;; NOTE we're currently inconsistent with sesame here...
        "\"Bonjour\"@fr" (str sesame-fr)
        rdf:langString (datatype-uri sesame-fr)))))
