(ns build
  (:require [clojure.tools.build.api :as b]))

(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))

(defn clean [_]
  (b/delete {:path "target"}))

(defn- run-clj-tests []
  ((requiring-resolve 'kaocha.result/failed?)
   ((requiring-resolve 'kaocha.api/run)
    ((requiring-resolve 'clojure.edn/read-string)
     (slurp "tests.edn")))))

(defn compile-cljs [_]
  ;;((requiring-resolve 'shadow.cljs.devtools.api/compile) :test)

  (when-not (zero? (:exit (b/process {:command-args ["yarn" "shadow-cljs" "compile" "test"]})))
    (throw (ex-info "CLJS compilation failed" {}))))

(defn test [_]
  (compile-cljs {})
  (let [clj-failed? (run-clj-tests)
        cljs-failed? (not (zero? (:exit (b/process {:command-args ["node" "./target/node-tests.js"]}))))]

    (when (or clj-failed? cljs-failed?)
      ;; force non-zero exit code
      (throw (ex-info "Tests failed" {})))))
