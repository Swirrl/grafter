(ns grafter-2.rdf4j.sparql.path-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing are]]
            [grafter-2.rdf4j.repository :as repo]
            [grafter-2.rdf4j.sparql :as sparql]
            [grafter-2.rdf4j.sparql.path :as p]
            [grafter.vocabularies.rdf :refer [rdfs:label rdf:a rdf:type rdfs:Class rdfs:subClassOf rdfs:subPropertyOf rdf:first rdf:rest]]
            [grafter.vocabularies.dcterms :refer [dcterms:title]]
            [grafter.vocabularies.foaf :refer [foaf:knows foaf:name]])
  (:import java.net.URI))

(def uri1 (URI. "http://test1"))
(def uri2 (URI. "http://test2"))
(def uri3 (URI. "http://test3"))
(def uri4 (URI. "http://test4"))
(def uri5 (URI. "http://test5"))

(defmacro throws? [ex-type expr]
  `(try
     ~expr
     (catch clojure.lang.ExceptionInfo e#
       (= ~ex-type (-> e# ex-data :type)))))

(deftest path-output-test
  (is (= "(<http://test1>/<http://test2>)"
         (p/string-value (p// uri1 uri2))))
  (is (= "(<http://test1>/<http://test2>/<http://test3>)"
         (p/string-value (p// uri1 uri2 uri3))))
  (is (= "(<http://test1>/(^<http://test2>)/<http://test3>)"
         (p/string-value (p// uri1 (p/- uri2) uri3))))
  (is (= "(<http://test1>/(!<http://test2>)/<http://test3>)"
         (p/string-value (p// uri1 (p/! uri2) uri3))))
  (is (= "(<http://test1>/(<http://test2>*)/<http://test3>)"
         (p/string-value (p// uri1 (p/* uri2) uri3))))
  (is (= "(<http://test1>/(<http://test2>+)/<http://test3>)"
         (p/string-value (p// uri1 (p/+ uri2) uri3))))
  (is (= "(<http://test1>/(<http://test2>?)/<http://test3>)"
         (p/string-value (p// uri1 (p/? uri2) uri3))))
  (is (= "(<http://test1>/(!<http://test2>)/((<http://test3>*)|(^<http://test4>))/<http://test5>)"
         (p/string-value (p// uri1 (p/! uri2) (p/| (p/* uri3) (p/- uri4)) uri5)))))

(defn p [s] (URI. (str "http://www.grafter.org/example#" s)))

(def link (p "link"))

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
                (sparql/query {:path (p/path link / link / link / link / link / -link / rdfs:label)}
                              conn))]
        (is (= o "Test E")))

      (let [[{:keys [s o]}]
            (-> "grafter/rdf4j/sparql/path-query.sparql"
                (sparql/query {:path (p/path (- (p "other")) / (- (p "lin2")) / link / link)}
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
    (is (= "(!<http://test1>)" (p/string-value (p/path ! uri)))))

  (let [uri (URI. "http://test1")]
    (is (= "(^<http://test1>)" (p/string-value (p/path - uri)))))

  (let [uri (URI. "http://test1")]
    (is (= "(<http://test1>*)" (p/string-value (p/path uri *)))))

  (let [uri (URI. "http://test1")]
    (is (= "(^(<http://test1>*))" (p/string-value (p/path - uri *)))))

  (let [uri (URI. "http://test1")]
    ;; Paths should really be equal, but equality not defined on AST nodes
    (is (= (p/string-value (p/path -uri*))
           (p/string-value (p/path - uri*))
           (p/string-value (p/path - uri *))
           (p/string-value (p/path -(URI. "http://test1")*))
           (p/string-value (p/path -((URI. "http://test1")*))))))

  (let [uri (URI. "http://test1")]
    (is (not= (p/string-value (p/path -uri*)) (p/string-value (p/path -uri *)))))

  (let [uri (URI. "http://test1")]
    (is (= (p/string-value (p/path - (URI. "test") / uri *))
           (p/string-value (p/path (- (URI. "test")) / (uri *))))))

  (testing "-prefix path syntax"
    (let [-prefix (URI. "http://test")] (is (= -prefix (p/path -prefix))))
    (let [prefix  (URI. "http://test")] (is (= "(^<http://test>)" (p/string-value (p/path -prefix))))))

  (testing "!prefix path syntax"
    (let [!prefix (URI. "http://test")] (is (= !prefix (p/path !prefix))))
    (let [prefix  (URI. "http://test")] (is (= "(!<http://test>)" (p/string-value (p/path !prefix))))))

  (testing "suffix+ path syntax"
    (let [suffix+ (URI. "http://test")] (is (= suffix+ (p/path suffix+))))
    (let [suffix  (URI. "http://test")] (is (= "(<http://test>+)" (p/string-value (p/path suffix+))))))

  (testing "!presuf* path syntax (ignoring ! as negation is invalid)"
    (let [!presuf* (URI. "http://test")] (is (= !presuf* (p/path !presuf*))))
    (let [!presuf  (URI. "http://test")] (is (= "(<http://test>*)" (p/string-value (p/path !presuf*))))))

  (testing "-presuf* path syntax"
    (let [-presuf* (URI. "http://test")] (is (= -presuf* (p/path -presuf*))))
    (let [-presuf  (URI. "http://test")] (is (= "(<http://test>*)" (p/string-value (p/path -presuf*)))))
    (let [presuf*  (URI. "http://test")] (is (= "(^<http://test>)" (p/string-value (p/path -presuf*)))))
    (let [presuf   (URI. "http://test")] (is (= "(^(<http://test>*))" (p/string-value (p/path -presuf*))))))

  (testing "Ambiguous syntaxes and/or compiler errors"
    ;; Use eval to move compiler errors to runtime to test macro
    ;; properly raises syntax errors when we have ambiguous bindings
    ;; in scope.
    (is (thrown? clojure.lang.Compiler$CompilerException
                 (eval '(let [-presuf* 0  presuf  0] (p/path -presuf*)))))
    (is (thrown? clojure.lang.Compiler$CompilerException
                 (eval '(let [-presuf  0  presuf  0] (p/path -presuf*)))))
    (is (thrown? clojure.lang.Compiler$CompilerException
                 (eval '(let [presuf* 0  presuf  0] (p/path -presuf*)))))
    (is (thrown? clojure.lang.Compiler$CompilerException
                 (eval '(let [-presuf* 0 -presuf  0] (p/path -presuf*)))))
    (is (thrown? clojure.lang.Compiler$CompilerException
                 (eval '(let [-presuf* 0  presuf* 0] (p/path -presuf*)))))
    (is (thrown? clojure.lang.Compiler$CompilerException
                 (eval '(let [-presuf** 'wut] (p/path -presuf*)))))
    (is (thrown? clojure.lang.Compiler$CompilerException
                 (eval '(let [presuf* 0] (p/path !presuf*)))))
    (is (thrown? clojure.lang.Compiler$CompilerException
                 (eval '(let [presuf 0] (p/path !presuf*)))))
    (is (thrown? clojure.lang.Compiler$CompilerException
                 (eval '(p/path ! uri *))))))


;; Ported path expressions from the W3C spec as example usage
;;
;; https://www.w3.org/TR/sparql11-query/#propertypath-examples
;;
;; NOTE these are just of the path expressions from the queries not
;; the complete queries

(def ex:motherOf (URI. "http://example/motherOf"))
(def ex:fatherOf (URI. "http://example/fatherOf"))

(deftest sparql-11-path-examples
  (are [path-sexp example]
      (= example (p/string-value path-sexp))

    ;; Alternatives: Match one or both possibilities
    (p/path dcterms:title | rdfs:label)
    "(<http://purl.org/dc/terms/title>|<http://www.w3.org/2000/01/rdf-schema#label>)"

    ;; Sequence: Find the name of any people (that Alice knows)
    (p/path foaf:knows / foaf:name)
    "(<http://xmlns.com/foaf/0.1/knows>/<http://xmlns.com/foaf/0.1/name>)"

    ;; Sequence: Find the names of people 2 "foaf:knows" links away
    (p/path foaf:knows / foaf:knows / foaf:name)
    "(<http://xmlns.com/foaf/0.1/knows>/(<http://xmlns.com/foaf/0.1/knows>/<http://xmlns.com/foaf/0.1/name>))"

    ;; Inverse Path Sequence: Find all the people who know someone (?x knows)
    (p/path foaf:knows / -foaf:knows)
    "(<http://xmlns.com/foaf/0.1/knows>/(^<http://xmlns.com/foaf/0.1/knows>))"

    ;; Arbitrary length match: Find the names of all the people that can be reached (from Alice) by foaf:knows
    (p/path foaf:knows+ / foaf:name)
    "((<http://xmlns.com/foaf/0.1/knows>+)/<http://xmlns.com/foaf/0.1/name>)"

    ;; Alternatives in an arbitrary length path
    (p/path (ex:motherOf | ex:fatherOf)+ )
    "((<http://example/motherOf>|<http://example/fatherOf>)+)"

    ;; Arbitrary length path match.  Some forms of limited inference are possible as well.
    ;; For example, for RDFS, all types and supertypes of a resource.
    ;; Also same example as "All resources and all their inferred types" from the spec
    (p/path rdf:type / rdfs:subClassOf*)
    "(<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>/(<http://www.w3.org/2000/01/rdf-schema#subClassOf>*))"

    ;; Subproperty
    (p/path rdfs:subPropertyOf*)
    "(<http://www.w3.org/2000/01/rdf-schema#subPropertyOf>*)"

    ;; Negated Property Paths: Find nodes connected but not by rdf:type (either way round)
    (p/path !(rdf:type | -rdf:type))
    "(!(<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>|(^<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>)))"

    ;; Elements in an RDF collection
    (p/path rdf:rest* / rdf:first)
    "((<http://www.w3.org/1999/02/22-rdf-syntax-ns#rest>*)/<http://www.w3.org/1999/02/22-rdf-syntax-ns#first>)"))
