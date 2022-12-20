(ns grafter-2.rdf.sparql-repository-test
  "Simple test to ensure that `grafter_2.rdf.SPARQLSession` is forwarding the
  rdf4j infer/reasoning query parameters to a remote server."
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [grafter-2.rdf4j.repository :as repo]
            [org.httpkit.server :refer [run-server]]
            [clojure.string :as string])
  (:import java.net.URLDecoder))

(def host "localhost")
(def port 5821)
(def db "http-test-db")
(def query-endpoint (format "http://%s:%s/%s/query" host port db))

(def simple-query "ASK { :rick a foaf:Person . }")

(defn parse-query-string [s]
  (->> (string/split s #"&")
       (map (fn [pair]
              (let [[k v] (string/split pair #"=")]
                [(keyword (URLDecoder/decode k)) (URLDecoder/decode v)])))
       (into {})))

(def handler
  (letfn [(respond [reasoning?]
            {:status 200
             :headers {"Content-Type" "text/boolean"}
             :body (pr-str reasoning?)})]
    (fn [{:keys [query-string] :as request}]
      (let [{:keys [infer reasoning query]} (parse-query-string query-string)]
        (assert (= query simple-query))
        (respond
         (and (Boolean/parseBoolean infer) (Boolean/parseBoolean reasoning)))))))

(defn http-kit-server-fixture [f]
  (let [stop (run-server #'handler {:port port})]
    (f)
    (stop)))

(use-fixtures :each http-kit-server-fixture)

(deftest simple-http-query-inference-test
  (let [repo (repo/sparql-repo query-endpoint)]
    (is (true? (repo/query (repo/->connection repo) simple-query :reasoning? true)))
    (is (false? (repo/query (repo/->connection repo) simple-query :reasoning? false)))))
