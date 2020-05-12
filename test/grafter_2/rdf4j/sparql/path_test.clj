(ns grafter-2.rdf4j.sparql.path-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [grafter-2.rdf4j.repository :as repo]
            [grafter-2.rdf4j.sparql :as sparql]
            [grafter-2.rdf4j.sparql.path :as p])
  (:import java.net.URI))

(def uri1 (URI. "http://test1"))
(def uri2 (URI. "http://test2"))
(def uri3 (URI. "http://test3"))
(def uri4 (URI. "http://test4"))
(def uri5 (URI. "http://test5"))

(defn output [x]
  (p/string-value x))

(defmacro throws? [ex-type expr]
  `(try
     ~expr
     (catch clojure.lang.ExceptionInfo e#
       (= ~ex-type (-> e# ex-data :type)))))

(deftest path-output-test
  (is (= "(<http://test1>/<http://test2>)" (output (p// uri1 uri2))))
  (is (= "(<http://test1>/<http://test2>/<http://test3>)" (output (p// uri1 uri2 uri3))))
  (is (= "(<http://test1>/(^<http://test2>)/<http://test3>)" (output (p// uri1 (p/! uri2) uri3))))
  (is (= "(<http://test1>/(<http://test2>*)/<http://test3>)" (output (p// uri1 (p/* uri2) uri3))))
  (is (= "(<http://test1>/(<http://test2>+)/<http://test3>)" (output (p// uri1 (p/+ uri2) uri3))))
  (is (= "(<http://test1>/(<http://test2>?)/<http://test3>)" (output (p// uri1 (p/? uri2) uri3))))
  (is (= "(<http://test1>/(<http://test2>{1})/<http://test3>)" (output (p// uri1 (p/n uri2 1) uri3))))
  (is (= "(<http://test1>/(<http://test2>{1})/<http://test3>)" (output (p// uri1 (p/n uri2 {1 1}) uri3))))
  (is (= "(<http://test1>/(<http://test2>{1,})/<http://test3>)" (output (p// uri1 (p/n uri2 {1 *}) uri3))))
  (is (= "(<http://test1>/(<http://test2>{1,})/<http://test3>)" (output (p// uri1 (p/n uri2 {1 '*}) uri3))))
  (is (= "(<http://test1>/(<http://test2>{1,})/<http://test3>)" (output (p// uri1 (p/n uri2 {1 p/*}) uri3))))
  (is (= "(<http://test1>/(<http://test2>{0,1})/<http://test3>)" (output (p// uri1 (p/n uri2 {0 1}) uri3))))
  (is (= "((<http://test1>^(<http://test2>{0,1}))|<http://test3>)" (output (p/| (p/! uri1 (p/n uri2 {0 1})) uri3))))
  (is (= "(<http://test1>/(<http://test2>^((<http://test3>*)|(^(<http://test4>{1})))^<http://test5>))"
         (output
          (p// uri1 (p/! uri2 (p/| (p/* uri3) (p/! (p/n uri4 1))) uri5))))))

(defn p [s] (URI. (str "http://www.grafter.org/example#" s)))

(def link (p "link"))
(def rdfs:label (URI. "http://www.w3.org/2000/01/rdf-schema#label"))

(defn results-contain? [results m]
  (boolean (some (partial = m) results)))

(deftest path-builder-test
  ;; Not all property path syntax works with a Sail repo, checking some that do.
  (let [r (repo/fixture-repo (io/resource "grafter/rdf4j/sparql/path.trig"))]
    (with-open [conn (repo/->connection r)]
      (let [[{:keys [s o]}]
            (-> "grafter/rdf4j/sparql/path-query.sparql"
                (sparql/query {:path (p/path link / link / link / link / link)}
                              conn))]
        (is (= s (p "a")))
        (is (= o (p "f"))))

      (let [[{:keys [s o]}]
            (-> "grafter/rdf4j/sparql/path-query.sparql"
                (sparql/query {:path (p/path link / link / link / link / link / rdfs:label)}
                              conn))]
        (is (= o "Test F")))

      (let [[{:keys [s o]}]
            (-> "grafter/rdf4j/sparql/path-query.sparql"
                (sparql/query {:path (p/path link / link / link / link / link / !link / rdfs:label)}
                              conn))]
        (is (= o "Test E")))

      (let [[{:keys [s o]}]
            (-> "grafter/rdf4j/sparql/path-query.sparql"
                (sparql/query {:path (p/path (! (p "other")) / (! (p "lin2")) / link / link)}
                              conn))]
        (is (= s (p "h")))
        (is (= o (p "e"))))

      (let [res (-> "grafter/rdf4j/sparql/path-query.sparql"
                    (sparql/query {:path (p/path link? / (p "lin2"))}
                                  conn))]
        (is (results-contain? res {:s (p "g") :o (p "g")}))
        (is (results-contain? res {:s (p "b") :o (p "g")})))

      (let [res (-> "grafter/rdf4j/sparql/path-query.sparql"
                    (sparql/query {:path (p/path (p "lin2") | (p "lin3"))}
                                  conn))]
        (is (results-contain? res {:s (p "c") :o (p "g")}))
        (is (results-contain? res {:s (p "c") :o (p "h")}))))))

(deftest path-syntax-test
  (let [uri (URI. "http://test1")]
    (is (= "(^<http://test1>)" (p/string-value (p/path ! uri)))))

  (testing "!prefix path syntax"
    (let [!prefix (URI. "http://test")] (is (= !prefix (p/path !prefix))))
    (let [prefix  (URI. "http://test")] (is (= "(^<http://test>)" (p/string-value (p/path !prefix))))))

  (testing "suffix+ path syntax"
    (let [suffix+ (URI. "http://test")] (is (= suffix+ (p/path suffix+))))
    (let [suffix  (URI. "http://test")] (is (= "(<http://test>+)" (p/string-value (p/path suffix+))))))

  (testing "!presuf* path syntax"
    (let [!presuf* (URI. "http://test")] (is (= !presuf* (p/path !presuf*))))
    (let [!presuf  (URI. "http://test")] (is (= "(<http://test>*)" (p/string-value (p/path !presuf*)))))
    (let [presuf*  (URI. "http://test")] (is (= "(^<http://test>)" (p/string-value (p/path !presuf*)))))
    (let [presuf   (URI. "http://test")] (is (= "(^(<http://test>*))" (p/string-value (p/path !presuf*))))))

  #_(testing "Ambiguous syntaxes and/or compiler errors"
    ;; NOTE: We can't actually unit test these because they throw compiler errors
    (let [!presuf* 0  presuf  0] (p/path !presuf*))
    (let [!presuf  0  presuf  0] (p/path !presuf*))
    (let [ presuf* 0  presuf  0] (p/path !presuf*))
    (let [!presuf* 0 !presuf  0] (p/path !presuf*))
    (let [!presuf* 0  presuf* 0] (p/path !presuf*))
    (let [!presuf** 'wut] (p/path !presuf*)))
  )
