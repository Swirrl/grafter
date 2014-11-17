(ns grafter.rdf-test
  (:require [clojure.test :refer :all]
            [grafter.rdf :refer :all]
            [grafter.rdf.protocols :refer [->Quad ->Triple]]
            [grafter.tabular :refer [make-dataset]]))

(def test-data [["http://a1" "http://b1" "http://c1" "http://graph1"]
                ["http://a2" "http://b2" "http://c2" "http://graph2"]])

(def first-quad (->Quad "http://a1" "http://b1" "http://c1" "http://graph1"))

(def second-quad (->Quad "http://a2" "http://b2" "http://c2" "http://graph2"))

(def first-turtle-template ["http://example.com/subjects/1"
                      ["http://example.com/p1" "http://example.com/o1"]
                      ["http://example.com/p2" "http://example.com/o2"]
                      ["http://example.com/p3" "http://example.com/o3"]])

(def second-turtle-template ["http://example.com/subjects/2"
                      ["http://example.com/p1" "http://example.com/o1"]
                      ["http://example.com/p2" "http://example.com/o2"]
                      ["http://example.com/p3" "http://example.com/o3"]])

(def turtle-template-blank-nodes ["http://example.com/subjects/1"
                      ["http://example.com/p1" [["http://example.com/blank/p1" "http://example.com/blank/o1"]]]
                      ["http://example.com/p2" "http://example.com/o2"]
                      ["http://example.com/p3" "http://example.com/o3"]])

(def invalid-blank-nodes-template ["http://example.com/subjects/1"
                      ["http://example.com/p1" []]
                      ["http://example.com/p2" "http://example.com/o2"]
                      ["http://example.com/p3" "http://example.com/o3"]])

(def first-graph-quad (->Quad "http://example.com/subjects/1" "http://example.com/p1" "http://example.com/o1" "http://example.com/graphs/1"))

(def first-triple (->Triple "http://example.com/subjects/1" "http://example.com/p1" "http://example.com/o1"))

(deftest graph-fn-test
  (testing "graph-fn"
    (testing "destructuring"
      (are [name column-names binding-form body]
        (testing name
          (let [ds (make-dataset test-data
                                 column-names)
                f (graph-fn [binding-form]
                            body)]

            (is (= first-quad
                   (first (f ds))))))

        "by :keys"
        [:a :b :c :d] {:keys [a b c d]}
        (graph d
               [a [b c]])

        "by :strs"
        ["a" "b" "c" "d"] {:strs [a b c d]}
        (graph d
               [a [b c]])

        "by map"
        ["a" :b "c" :d] {a "a" b :b c "c" graf :d}
        (graph graf
               [a [b c]])

        "by position (vector)"
        [:a :b :c :d] [one two three graf]
        (graph graf
               [one [two three]])))

    (testing "concatenates sequences returned by each form in the body"
      (let [ds (make-dataset test-data)
                f (graph-fn [[one two three graf]]
                            (graph graf
                                   [one [two three]]))]

        (is (= [first-quad second-quad]
               (f ds)))))))

(deftest triplify-test
  (testing "triplify"
    (testing "with one template"
      (let [g (triplify first-turtle-template)]
        (is (= 3
               (count g)))
        (is (= first-triple
               (first g)
               ))))
    (testing "with multiple templates"
      (let [g (triplify first-turtle-template second-turtle-template)]
        (is (= 6
               (count g)))))))

(deftest graph-test
  (testing "graph function"
    (testing "with all nodes"
      (let [g (graph "http://example.com/graphs/1" first-turtle-template second-turtle-template)]
        (is (= 6
               (count g)))
        (is (= first-graph-quad
               (first g)))
        (let [[s p o c] (first g)]
          (is (= "http://example.com/subjects/1" s))
          (is (= "http://example.com/p1" p))
          (is (= "http://example.com/o1" o))
          (is (= "http://example.com/graphs/1" c)))))
    (testing "with blank nodes"
      (let [g (graph "http://example.com/graphs/1" turtle-template-blank-nodes)]
        (let [[s p o c] (first g)]
          (is (= "http://example.com/subjects/1" s))
          (is (= "http://example.com/p1" p))
          (is (keyword? o))
          (is (= "http://example.com/graphs/1" c))
          (is (= o
               (first (keep (fn [[k v]] (if (= o k) k)) g)))))))))

(deftest quads-and-triples-test
  (testing "Triples"
    (testing "support positional destructuring"
      (let [triple (->Triple "http://subject/" "http://predicate/" "http://object/")
            [s p o] triple]

        (is (= "http://subject/" s))
        (is (= "http://predicate/" p))
        (is (= "http://object/" o)))))

  (testing "Quads"
    (testing "support positional destructuring"
      (let [quad (->Quad "http://subject/" "http://predicate/" "http://object/" "http://context/")
            [s p o c] quad]

        (is (= "http://subject/" s))
        (is (= "http://predicate/" p))
        (is (= "http://object/" o))
        (is (= "http://context/" c))))))
