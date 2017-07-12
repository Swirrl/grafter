(ns grafter.rdf.templater-test
  (:require [clojure.test :refer :all]
            [grafter.rdf :refer :all]
            [grafter.rdf.templater :refer [graph triplify]]))

(def first-turtle-template ["http://example.com/subjects/1"
                            ["http://example.com/p1" "http://example.com/o1"]
                            ["http://example.com/p2" "http://example.com/o2"]
                            ["http://example.com/p3" "http://example.com/o3"]])

(def second-turtle-template ["http://example.com/subjects/2"
                             ["http://example.com/p1" "http://example.com/o1"]
                             ["http://example.com/p2" 2]
                             ["http://example.com/p3" "http://example.com/o3"]])

(def invalid-blank-nodes-template ["http://example.com/subjects/1"
                                   ["http://example.com/p1" []]
                                   ["http://example.com/p2" "http://example.com/o2"]
                                   ["http://example.com/p3" "http://example.com/o3"]])

(def turtle-template-blank-nodes ["http://example.com/subjects/1"
                                  ["http://example.com/p1" [["http://example.com/blank/p1"
                                                             [["http://example.com/blank/p2" "http://example.com/blank/o2"]]]]]
                                  ["http://example.com/p2" "http://example.com/o2"]
                                  ["http://example.com/p3" "http://example.com/o3"]])
(deftest triplify-test
  (testing "triplify"
    (let [first-turtle-template ["http://example.com/subjects/1"
                                 ["http://example.com/p1" "http://example.com/o1"]
                                 ["http://example.com/p2" "http://example.com/o2"]
                                 ["http://example.com/p3" "http://example.com/o3"]]

          second-turtle-template ["http://example.com/subjects/2"
                                  ["http://example.com/p1" "http://example.com/o1"]
                                  ["http://example.com/p2" 2]
                                  ["http://example.com/p3" "http://example.com/o3"]]]



      (testing "with one template"
        (let [triples (triplify first-turtle-template)]
          (is (= 3
                 (count triples)))
          (is (= (->Triple "http://example.com/subjects/1" "http://example.com/p1" "http://example.com/o1")
                 (first triples)
                 ))
          (let [[s p o] (first triples)]
            (is (= "http://example.com/subjects/1" s))
            (is (= "http://example.com/p1" p))
            (is (= "http://example.com/o1" o)))))
      (testing "with multiple templates"
        (let [triples (triplify first-turtle-template second-turtle-template)]
          (is (= 6
                 (count triples))))))

    (testing "with valid blank node"

      (let [triples (triplify ["http://example.com/subjects/1"
                               ["http://example.com/p1" [["http://example.com/blank/p1" "http://example.com/blank/o1"]]]
                               ["http://example.com/p2" "http://example.com/o2"]
                               ["http://example.com/p3" "http://example.com/o3"]])]

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
      (let [triples (triplify
                     turtle-template-blank-nodes)]

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
      (let [triples (triplify ["http://example.com/subjects/1"
                               ["http://example.com/p1" [["http://eAxample.com/blank/p1" 1]]]
                               ["http://example.com/p2" "http://example.com/o2"]
                               ["http://example.com/p3" "http://example.com/o3"]])]
        (let [[s p o] (first triples)]
          (is (= "http://example.com/subjects/1" s))
          (is (= "http://example.com/p1" p))
          (is (keyword? o))
          (is (some (fn [[k v]] (= k o)) triples)))
        (is (= 4
               (count triples)))
        (is (filter (fn [n] (and (= "http://example.com/blank/p1" (predicate n))
                                (= 1 (object n)))) triples))))

    #_(testing "with an empty vector blank node"
      (is (thrown? java.lang.AssertionError

                   (triplify ["http://example.com/subjects/1"
                              ["http://example.com/p1" []]
                              ["http://example.com/p2" "http://example.com/o2"]]))))

    #_(testing "with an invalid second nested blank node"
      (is (thrown? java.lang.AssertionError

                   (triplify ["http://example.com/subjects/1"
                              ["http://example.com/p1" [["http://www.example.com/blank/p1" [[1]]]]]
                              ["http://example.com/p2" "http://example.com/o2"]]))))

    #_(testing "with a nested empty vector blank node"
      (is (thrown? java.lang.AssertionError

                   (triplify ["http://example.com/subjects/1"
                              ["http://example.com/p1" [["http://www.example.com/blank/p1" []]]]
                              ["http://example.com/p2" "http://example.com/o2"]]))))

    #_(testing "with a nested blank node with backwards arguments"
      (is (thrown? java.lang.AssertionError

                   (triplify ["http://example.com/subjects/1"
                              ["http://example.com/p1" [["http://www.example.com/blank/p1"
                                                         [[1 "http://example.com/blank/o1"]]]]]
                              ["http://example.com/p2" "http://example.com/o2"]]))))

    #_(testing "with multiple templates, one of which has an invalid blank node"
      (is (thrown? java.lang.AssertionError
                   (triplify first-turtle-template invalid-blank-nodes-template))))))

(deftest graph-test
  (testing "graph function"
    (testing "with all nodes"
      (let [quads (graph "http://example.com/graphs/1" first-turtle-template second-turtle-template)]
        (is (= 6
               (count quads)))
        (is (= (->Quad "http://example.com/subjects/1" "http://example.com/p1" "http://example.com/o1" "http://example.com/graphs/1")
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
          (is (some (fn [[k v]] (= k o)) quads)))))))
