(ns grafter.rdf-test
  (:require [clojure.test :refer :all]
            [grafter.rdf :refer :all]
            [grafter.rdf.protocols :refer [->Quad ->Triple]]
            [grafter.tabular :refer [make-dataset]]))

(def test-data [["http://a1" "http://b1" "http://c1" "http://graph1"]
                ["http://a2" "http://b2" "http://c2" "http://graph2"]])

(def first-quad (->Quad "http://a1" "http://b1" "http://c1" "http://graph1"))

(def second-quad (->Quad "http://a2" "http://b2" "http://c2" "http://graph2"))

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

(def first-graph-quad (->Quad "http://example.com/subjects/1" "http://example.com/p1" "http://example.com/o1" "http://example.com/graphs/1"))

(def first-triple (->Triple "http://example.com/subjects/1" "http://example.com/p1" "http://example.com/o1"))

(deftest quad-test
  (testing "quads function"
    (let [q (quad "http://example.com/graphs/1" first-triple)]
      (is (= first-graph-quad q)))))

(def first-turtle-template ["http://example.com/subjects/1"
                      ["http://example.com/p1" "http://example.com/o1"]
                      ["http://example.com/p2" "http://example.com/o2"]
                      ["http://example.com/p3" "http://example.com/o3"]])

(def second-turtle-template ["http://example.com/subjects/2"
                      ["http://example.com/p1" "http://example.com/o1"]
                      ["http://example.com/p2" 2]
                      ["http://example.com/p3" "http://example.com/o3"]])

(def turtle-template-blank-nodes ["http://example.com/subjects/1"
                      ["http://example.com/p1" [["http://example.com/blank/p1" "http://example.com/blank/o1"]]]
                      ["http://example.com/p2" "http://example.com/o2"]
                      ["http://example.com/p3" "http://example.com/o3"]])

(def turtle-template-nested-blank-nodes ["http://example.com/subjects/1"
                      ["http://example.com/p1" [["http://example.com/blank/p1"
                                                 [["http://example.com/blank/p2" "http://example.com/blank/o2"]]]]]
                      ["http://example.com/p2" "http://example.com/o2"]
                      ["http://example.com/p3" "http://example.com/o3"]])

(def turtle-template-blank-nodes-with-literal ["http://example.com/subjects/1"
                      ["http://example.com/p1" [["http://example.com/blank/p1" 1]]]
                      ["http://example.com/p2" "http://example.com/o2"]
                      ["http://example.com/p3" "http://example.com/o3"]])


(def invalid-blank-nodes-template ["http://example.com/subjects/1"
                      ["http://example.com/p1" []]
                      ["http://example.com/p2" "http://example.com/o2"]
                      ["http://example.com/p3" "http://example.com/o3"]])

(def invalid-blank-nodes-two ["http://example.com/subjects/1"
                      ["http://example.com/p1" [["http://www.example.com/blank/p1" [[1]]]]]
                      ["http://example.com/p2" "http://example.com/o2"]
                      ["http://example.com/p3" "http://example.com/o3"]])

(def invalid-blank-nodes-three ["http://example.com/subjects/1"
                      ["http://example.com/p1" [["http://www.example.com/blank/p1" []]]]
                      ["http://example.com/p2" "http://example.com/o2"]
                      ["http://example.com/p3" "http://example.com/o3"]])

(def invalid-blank-nodes-four ["http://example.com/subjects/1"
                      ["http://example.com/p1" [["http://www.example.com/blank/p1"
                                                 [[1 "http://example.com/blank/o1"]]]]]
                      ["http://example.com/p2" "http://example.com/o2"]
                      ["http://example.com/p3" "http://example.com/o3"]])

(deftest triplify-test
  (testing "triplify"
    (testing "with one template"
      (let [triples (triplify first-turtle-template)]
        (is (= 3
               (count triples)))
        (is (= first-triple
               (first triples)
               ))
        (let [[s p o] (first triples)]
          (is (= "http://example.com/subjects/1" s))
          (is (= "http://example.com/p1" p))
          (is (= "http://example.com/o1" o)))))
    (testing "with multiple templates"
      (let [triples (triplify first-turtle-template second-turtle-template)]
        (is (= 6
               (count triples)))))
    (testing "with valid blank node"
      (let [triples (triplify turtle-template-blank-nodes)]
        (let [[s p o] (first triples)]
          (is (= "http://example.com/subjects/1" s))
          (is (= "http://example.com/p1" p))
          (is (keyword? o))
          (is (some (fn [[k v]] (= k o)) triples)))
        (is (= 4
               (count triples)))
        (is (filter (fn [n] (and (= "http://example.com/blank/p1" (predicate n))
                                (= "http://example.com/blank/o1" (object n)))) triples))))
    (testing "with valid nested blank nodes"
      (let [triples (triplify turtle-template-nested-blank-nodes)]
        (let [[s p o] (first triples)]
          (is (= "http://example.com/subjects/1" s))
          (is (= "http://example.com/p1" p))
          (is (keyword? o))
          (is (some (fn [[k v]] (= k o)) triples)))
        (is (= 5
               (count triples)))
        (is (filter (fn [n] (and (= "http://example.com/blank/p2" (predicate n))
                                (= "http://example.com/blank/o2" (object n)))) triples))))
    (testing "with valid blank node and literal"
      (let [triples (triplify turtle-template-blank-nodes-with-literal)]
        (let [[s p o] (first triples)]
          (is (= "http://example.com/subjects/1" s))
          (is (= "http://example.com/p1" p))
          (is (keyword? o))
          (is (some (fn [[k v]] (= k o)) triples)))
        (is (= 4
               (count triples)))
        (is (filter (fn [n] (and (= "http://example.com/blank/p1" (predicate n))
                                (= 1 (object n)))) triples))))
    (testing "with an empty vector blank node"
      (is (thrown? java.lang.AssertionError
                   (triplify invalid-blank-nodes-template))))
    (testing "with an invalid second nested blank node"
      (is (thrown? java.lang.AssertionError
                   (triplify invalid-blank-nodes-two))))
    (testing "with a nested empty vector blank node"
      (is (thrown? java.lang.AssertionError
                   (triplify invalid-blank-nodes-three))))
    (testing "with a nested blank node with backwards arguments"
      (is (thrown? java.lang.AssertionError
                   (triplify invalid-blank-nodes-four))))
    (testing "with multiple templates, one of which has an invalid blank node"
      (is (thrown? java.lang.AssertionError
                   (triplify first-turtle-template invalid-blank-nodes-template))))))

(deftest graph-test
  (testing "graph function"
    (testing "with all nodes"
      (let [quads (graph "http://example.com/graphs/1" first-turtle-template second-turtle-template)]
        (is (= 6
               (count quads)))
        (is (= first-graph-quad
               (first quads)))
        (let [[s p o c] (first quads)]
          (is (= "http://example.com/subjects/1" s))
          (is (= "http://example.com/p1" p))
          (is (= "http://example.com/o1" o))
          (is (= "http://example.com/graphs/1" c))
          (testing "accessor methods"
            (is (= "http://example.com/subjects/1" (subject (first quads))))
            (is (= "http://example.com/p1" (predicate (first quads))))
            (is (= "http://example.com/o1" (object (first quads))))
            (is (= "http://example.com/graphs/1" (context (first quads))))))))
    (testing "with blank nodes"
      (let [quads (graph "http://example.com/graphs/1" turtle-template-blank-nodes)]
        (let [[s p o c] (first quads)]
          (is (= "http://example.com/subjects/1" s))
          (is (= "http://example.com/p1" p))
          (is (keyword? o))
          (is (= "http://example.com/graphs/1" c))
          (is (some (fn [[k v]] (= k o)) quads)))))
    (testing "with an incorrectly specified blank node"
      (is (thrown? java.lang.AssertionError
                   (graph "http://example.com/graphs/1" invalid-blank-nodes-template))))))

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
