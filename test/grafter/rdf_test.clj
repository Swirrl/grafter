(ns grafter.rdf-test
  (:require [grafter.rdf :refer :all]
            [grafter.rdf.protocols :refer [raw-value datatype-uri] :as proto]
            [grafter.rdf.repository :as repo]
            [clojure.test :refer :all])
  (:import [java.net URI]))

(deftest triple=-test
  (testing "triple= quads"
    (is (triple= (->Quad "http://subject/" "http://predicate/" "http://object/" "http://context/")
                 (->Quad "http://subject/" "http://predicate/" "http://object/" nil)
                 (->Quad "http://subject/" "http://predicate/" "http://object/" "http://context/2"))))

  (testing "not triple="
    (triple= (->Quad "http://subject/1" "http://predicate/" "http://object/" "http://context/")
             (->Quad "http://subject/2" "http://predicate/" "http://object/" "http://context/"))))


(deftest literal-test
  (let [lit (literal "10" "http://www.w3.org/2001/XMLSchema#byte")]
    (is (= (URI. "http://www.w3.org/2001/XMLSchema#byte") (datatype-uri lit)))
    (is (= "10" (raw-value lit)))))

(deftest language-test
  (is (thrown? AssertionError
               (language "foo" nil))))

(defrecord BatchSizeRecordingRepository [repo batch-sizes])

(extend-type BatchSizeRecordingRepository
  proto/ITripleWriteable
  (proto/add
    ([{:keys [repo batch-sizes] :as this} quads]
     (swap! batch-sizes conj (count quads))
     (proto/add repo quads)
     this)
    ([{:keys [repo batch-sizes] :as this} graph triples]
     (swap! batch-sizes conj (count triples))
     (proto/add repo graph triples)
     this))

  proto/ITripleDeleteable
  (proto/delete
    ([{:keys [repo batch-sizes]} quads]
      (swap! batch-sizes conj (count quads))
      (proto/delete repo quads))

    ([{:keys [repo batch-sizes]} graph triples]
      (swap! batch-sizes conj (count triples))
      (proto/delete repo graph triples))))

(deftest add-batched-test
  (let [triples (map (fn [i] (->Triple (URI. (str "http://subject" i)) (URI. (str "http://predicate" i)) (URI. (str "http://object" i)))) (range 1 11))
        graph (URI. "http://test-graph")]
    (testing "Adds all triples"
      (let [repo (repo/repo)]
        (add-batched repo triples)
        (is (= (set triples) (set (statements repo))))))

    (testing "Adds all triples with graph"
      (let [expected-quads (map #(assoc % :c graph) triples)
            repo (repo/repo)]
        (add-batched repo graph triples)
        (is (= (set expected-quads) (set (statements repo))))))

    (testing "Adds all triples in sized batches"
      (let [repo (repo/repo)
            batch-sizes (atom [])
            recording-repo (->BatchSizeRecordingRepository repo batch-sizes)]
        (add-batched recording-repo triples 3)
        (is (= (set triples) (set (statements repo))))
        (is (= [3 3 3 1] @batch-sizes))))

    (testing "Adds all triples with graph in sized batches"
      (let [expected-quads (map #(assoc % :c graph) triples)
            repo (repo/repo)
            batch-sizes (atom [])
            recording-repo (->BatchSizeRecordingRepository repo batch-sizes)]
        (add-batched recording-repo graph triples 5)
        (is (= (set expected-quads) (set (statements repo))))
        (is (= [5 5] @batch-sizes))))))

(defn- triple->quad [graph triple]
  (assoc triple :c graph))

(deftest delete-batched-test
  (let [initial-triples (map (fn [i] (->Triple (URI. (str "http://subject" i)) (URI. (str "http://predicate" i)) (URI. (str "http://object" i)))) (range 1 11))
        [to-keep to-delete] (split-at 4 initial-triples)
        graph (URI. "http://test-graph")
        make-quads (fn [triples] (map #(triple->quad graph %) triples))]
    (testing "Deletes all triples"
      (let [repo (repo/repo)]
        (add repo initial-triples)
        (delete-batched repo to-delete)
        (is (= (set to-keep) (set (statements repo))))))

    (testing "Deletes all triples with graph"
      (let [repo (repo/repo)]
        (add repo (make-quads initial-triples))
        (delete-batched repo graph to-delete)
        (is (= (set (make-quads to-keep)) (set (statements repo))))))

    (testing "Deletes all triples in sized batches"
      (let [repo (repo/repo)
            batch-sizes (atom [])
            recording-repo (->BatchSizeRecordingRepository repo batch-sizes)]
        (add repo initial-triples)
        (delete-batched recording-repo to-delete 4)
        (is (= (set to-keep) (set (statements repo))))
        (is (= [4 2] @batch-sizes))))

    (testing "Deletes all triples with graph in sized batches"
      (let [repo (repo/repo)
            batch-sizes (atom [])
            recording-repo (->BatchSizeRecordingRepository repo batch-sizes)]
        (add repo (make-quads initial-triples))
        (delete-batched recording-repo graph to-delete 4)
        (is (= (set (make-quads to-keep)) (set (statements repo))))
        (is (= [4 2] @batch-sizes))))))
