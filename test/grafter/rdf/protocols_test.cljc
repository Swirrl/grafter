(ns grafter.rdf.protocols-test
  (:require [grafter.rdf.protocols :refer [->Quad ->Triple language
                                           lang raw-value datatype-uri]])

  #?(:clj (:require [clojure.test :as t]
                    [grafter.rdf :refer [language]])
     :cljs (:require [cljs.test :as t :include-macros true]))
  #?(:clj (:import [org.openrdf.model.impl LiteralImpl])))

(def test-data [["http://a1" "http://b1" "http://c1" "http://graph1"]
                ["http://a2" "http://b2" "http://c2" "http://graph2"]])

(def first-quad (pr/->Quad "http://a1" "http://b1" "http://c1" "http://graph1"))

(def second-quad (pr/->Quad "http://a2" "http://b2" "http://c2" "http://graph2"))

(t/deftest quads-test
  (t/is (not= first-quad second-quad)))

#?(:clj
    (t/deftest quads-test
      (t/testing "Quads"
        (t/testing "support positional destructuring"
          (let [quad (pr/->Quad "http://subject/" "http://predicate/" "http://object/" "http://context/")
                [s p o c] quad]

            (t/is (= "http://subject/" s))
            (t/is (= "http://predicate/" p))
            (t/is (= "http://object/" o))
            (t/is (= "http://context/" c)))))))

#?(:clj
    (t/deftest rdf-strings-test
      (t/testing "RDF Strings"
        (let [en (language "Hello" :en)
              sesame-fr (LiteralImpl. "Bonjour" "fr")
              sesame-nolang (LiteralImpl. "Bonjour")]
          (t/are [expected test-val]
              (t/is (= expected test-val))

              :en (lang en)
              "Hello" (raw-value en)
              "Hello" (str en)
              rdf:langString (datatype-uri en)

              :fr (lang sesame-fr)
              "Bonjour" (raw-value sesame-fr)

              ;; NOTE we're currently inconsistent with sesame here...
              "\"Bonjour\"@fr" (str sesame-fr)
              rdf:langString (datatype-uri sesame-fr))))))


#?(:clj
   (t/deftest raw-value-test
     (t/testing "Default implementation"
       (let [o (Object.)]
         (t/is (= o (raw-value o))
               "Returns identity on all Objects by default")))

     (t/testing "RDF Literals"
       (t/is (= "I stepped into an avalanche"
                (raw-value (LiteralImpl. "I stepped into an avalanche")))))))
