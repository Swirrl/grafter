(ns grafter.rdf.protocols-test
  (:require [clojure.test :refer :all]
            [grafter.rdf.protocols :refer :all]))

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
    (let [en (->RDFString "Hello" :en)
          nolang (->RDFString "Yo" nil)
          sesame-fr (org.openrdf.model.impl.LiteralImpl. "Bonjour" "fr")
          sesame-nolang (org.openrdf.model.impl.LiteralImpl. "Bonjour")]
      (are [expected test-val]
          (is (= expected test-val))

        :en (language en)
        "Hello" (raw-value en)
        rdf:langString (data-type-uri en)

        nil (language nolang)
        "Yo" (raw-value nolang)
        xsd:string (data-type-uri nolang)

        :fr (language sesame-fr)
        "Bonjour" (raw-value sesame-fr)
        rdf:langString (data-type-uri sesame-fr)
        ))))


(deftest literals-test
  )
