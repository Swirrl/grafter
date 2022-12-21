(ns build
  (:require [clojure.tools.build.api :as b]
            [org.corfield.build :as bb]))

(defn- aborting-process [opts]
  (let [exit (:exit (b/process opts))]
    (when-not (zero? exit)
      (throw (ex-info "Process call returned non-zero exit code"
                      {:exit-code exit
                       :opts opts})))))

(defn test-all [_]
  (aborting-process {:dir "grafter.core"
                     ;; This project uses tools.build to test under both cljs and clj
                     :command-args ["clojure" "-T:build" "test"]})
  (aborting-process {:dir "grafter.io"
                     :command-args ["clojure" "-M:test"]})
  (aborting-process {:dir "grafter.repository"
                     :command-args ["clojure" "-M:test"]})
  (println "Testing done"))


(defn build [_]
  (test-all nil))
