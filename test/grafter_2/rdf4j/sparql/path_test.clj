(ns grafter-2.rdf4j.sparql.path-test
  (:require [grafter-2.rdf4j.sparql.path :as p]
            [clojure.test :refer [deftest is testing]]
            [grafter-2.rdf4j.io :as rio]
            [grafter-2.rdf4j.sparql :as sparql]
            [grafter-2.rdf4j.repository :as repo]
            [clojure.java.io :as io])
  (:import java.net.URI
           org.eclipse.rdf4j.repository.sparql.query.QueryStringUtil))

(def uri1 (URI. "http://test1"))
(def uri2 (URI. "http://test2"))
(def uri3 (URI. "http://test3"))
(def uri4 (URI. "http://test4"))
(def uri5 (URI. "http://test5"))

(defn output [x]
  (.stringValue (rio/->backend-type x)))

(defmacro throws? [ex-type expr]
  `(try
     ~expr
     (catch clojure.lang.ExceptionInfo e#
       (= ~ex-type (-> e# ex-data :type)))))

(deftest path-output-test
  (is (= "http://test1" (output uri1)))
  (is (= "http://test1>/<http://test2" (output (p// uri1 uri2))))
  (is (= "http://test1>/<http://test2>/<http://test3" (output (p// uri1 uri2 uri3))))
  (is (= "http://test1>/^<http://test2>/<http://test3" (output (p// uri1 (p/! uri2) uri3))))
  (is (= "http://test1>/<http://test2>*/<http://test3" (output (p// uri1 (p/* uri2) uri3))))
  (is (= "http://test1>/<http://test2>+/<http://test3" (output (p// uri1 (p/+ uri2) uri3))))
  (is (= "http://test1>/<http://test2>?/<http://test3" (output (p// uri1 (p/? uri2) uri3))))
  (is (= "http://test1>/<http://test2>{1}/<http://test3" (output (p// uri1 (p/n uri2 1) uri3))))
  (is (= "http://test1>/<http://test2>{1}/<http://test3" (output (p// uri1 (p/n uri2 {1 1}) uri3))))
  (is (= "http://test1>/<http://test2>{1,}/<http://test3" (output (p// uri1 (p/n uri2 {1 *}) uri3))))
  (is (= "http://test1>/<http://test2>{1,}/<http://test3" (output (p// uri1 (p/n uri2 {1 '*}) uri3))))
  (is (= "http://test1>/<http://test2>{1,}/<http://test3" (output (p// uri1 (p/n uri2 {1 p/*}) uri3))))
  (is (= "http://test1>/<http://test2>{0,1}/<http://test3" (output (p// uri1 (p/n uri2 {0 1}) uri3))))
  (is (= "http://test1>^<http://test2>{0,1}|<http://test3" (output (p/| (p/! uri1 (p/n uri2 {0 1})) uri3))))
  (is (throws? ::p/prefix-conversion-exception (output (p/! uri1))))
  (is (throws? ::p/suffix-conversion-exception (output (p/* uri1))))
  (is (= "http://test1>/<http://test2>^<http://test3>*|^<http://test4>{1}^<http://test5"
         (output
          (p// uri1 (p/! uri2 (p/| (p/* uri3) (p/! (p/n uri4 1))) uri5))))))

(defn foaf [s] (URI. (str "http://xmlns.com/foaf/0.1/" s)))
(defn p [s] (URI. (str "http://www.grafter.org/example#" s)))

(def rdfs:label (URI. "http://www.w3.org/2000/01/rdf-schema#label"))

(deftest query-test
  (let [r (repo/fixture-repo (io/resource "grafter/rdf4j/sparql/path.trig"))]
    (with-open [conn (repo/->connection r)]
      (let [qstr (slurp (io/resource "grafter/rdf4j/sparql/path-query.sparql"))
            q (#'sparql/-query "grafter/rdf4j/sparql/path-query.sparql"
                               {:path (p// (p "p") rdfs:label)}
                               conn)]
        ;; Why?
        ;; This only works for SPARQL Connections, and the Sail Repo/Conn here
        ;; are not SPARQL. This ensures the hookup is working (as best we can).
        (.contains (QueryStringUtil/getTupleQueryString qstr (.getBindings q))
                   "?s <http://www.grafter.org/example#p>/<http://www.w3.org/2000/01/rdf-schema#label> ?o")))))
