(ns grafter-2.rdf.batching-test
  (:require [clojure.test :refer :all]
            [grafter-2.rdf.protocols :refer :all]
            [grafter-2.rdf4j.io :as rio]
            [grafter-2.rdf4j.repository :as repo]
            )
  (:import java.net.URI))

(defrecord BatchSizeRecordingRepository [repo batch-sizes])

(extend-type BatchSizeRecordingRepository
  ITripleWriteable
  (add
    ([{:keys [repo batch-sizes] :as this} quads]
     (swap! batch-sizes conj (count quads))
     (add repo quads)
     this)
    ([{:keys [repo batch-sizes] :as this} graph triples]
     (swap! batch-sizes conj (count triples))
     (add repo graph triples)
     this))

  ITripleDeleteable
  (delete
    ([{:keys [repo batch-sizes]} quads]
     (swap! batch-sizes conj (count quads))
     (delete repo quads))

    ([{:keys [repo batch-sizes]} graph triples]
     (swap! batch-sizes conj (count triples))
     (delete repo graph triples))))

(deftest add-batched-test
  (let [triples (map (fn [i] (->Triple (URI. (str "http://subject" i)) (URI. (str "http://predicate" i)) (URI. (str "http://object" i)))) (range 1 11))
        graph (URI. "http://test-graph")]
    (testing "Adds all triples"
      (with-open [repo (repo/->connection (repo/sail-repo))]
        (add-batched repo triples)
        (is (= (set triples) (set (rio/statements repo))))))

    (testing "Adds all triples with graph"
      (let [expected-quads (map #(assoc % :c graph) triples)]
        (with-open [repo (repo/->connection (repo/sail-repo))]
          (add-batched repo graph triples)
          (is (= (set expected-quads) (set (rio/statements repo)))))))

    (testing "Adds all triples in sized batches"
      (with-open [repo (repo/->connection (repo/sail-repo))]
        (let [batch-sizes (atom [])
              recording-repo (->BatchSizeRecordingRepository repo batch-sizes)]
          (add-batched recording-repo triples 3)
          (is (= (set triples) (set (rio/statements repo))))
          (is (= [3 3 3 1] @batch-sizes)))))

    (testing "Adds all triples with graph in sized batches"
      (with-open [repo (repo/->connection (repo/sail-repo))]
        (let [expected-quads (map #(assoc % :c graph) triples)
              batch-sizes (atom [])
              recording-repo (->BatchSizeRecordingRepository repo batch-sizes)]
          (add-batched recording-repo graph triples 5)
          (is (= (set expected-quads) (set (rio/statements repo))))
          (is (= [5 5] @batch-sizes)))))))

(defn- triple->quad [graph triple]
  (assoc triple :c graph))

(deftest delete-batched-test
  (let [initial-triples (map (fn [i] (->Triple (URI. (str "http://subject" i)) (URI. (str "http://predicate" i)) (URI. (str "http://object" i)))) (range 1 11))
        [to-keep to-delete] (split-at 4 initial-triples)
        graph (URI. "http://test-graph")
        make-quads (fn [triples] (map #(triple->quad graph %) triples))]
    (testing "Deletes all triples"
      (with-open [repo (repo/->connection (repo/sail-repo))]
        (add repo initial-triples)
        (delete-batched repo to-delete)
        (is (= (set to-keep) (set (rio/statements repo))))))

    (testing "Deletes all triples with graph"
      (with-open [repo (repo/->connection (repo/sail-repo))]
        (add repo (make-quads initial-triples))
        (delete-batched repo graph to-delete)
        (is (= (set (make-quads to-keep)) (set (rio/statements repo))))))

    (testing "Deletes all triples in sized batches"
      (with-open [repo (repo/->connection (repo/sail-repo))]
        (let [batch-sizes (atom [])
              recording-repo (->BatchSizeRecordingRepository repo batch-sizes)]
          (add repo initial-triples)
          (delete-batched recording-repo to-delete 4)
          (is (= (set to-keep) (set (rio/statements repo))))
          (is (= [4 2] @batch-sizes)))))

    (testing "Deletes all triples with graph in sized batches"
      (with-open [repo (repo/->connection (repo/sail-repo))]
        (let [batch-sizes (atom [])
              recording-repo (->BatchSizeRecordingRepository repo batch-sizes)]
          (add repo (make-quads initial-triples))
          (delete-batched recording-repo graph to-delete 4)
          (is (= (set (make-quads to-keep)) (set (rio/statements repo))))
          (is (= [4 2] @batch-sizes)))))))
