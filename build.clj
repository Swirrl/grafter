(ns build
  (:require [clojure.tools.build.api :as b]
            [org.corfield.build :as bb]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(def version (str/replace (or (System/getenv "CIRCLE_TAG")
                              "v3.0.0-SNAPSHOT")
                            #"^v" ""))

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

(defmacro with-project-root [dir & forms]
  `(let [orig-root# @#'b/*project-root*] ;; yes this is correct, my cat didn't run over my keyboard I promise! :-)
     (try
       (b/set-project-root! ~dir)
       (let [ret# (do ~@forms)]
         ret#)
       (finally
         (b/set-project-root! orig-root#)))))

(defn- build-submodule [& {:keys [mod-name lib pom] :as opts}]
  (with-project-root mod-name
    (bb/clean {:target "./target"})
    (let [jar-file (format "target/%s-%s.jar" (name lib) version)
          basis (b/create-basis {})]
      (-> opts
          (assoc :basis basis)
          (bb/jar)))))

(def submodules ["grafter.core" "grafter.io" "grafter.repository"])

(defn- canonicalise-file [& f]
  (str (apply io/file (.getCanonicalFile (io/file (or b/*project-root* ".")))
              f)))

(defn build-all
  "Build all submodules locally"
  [opts]
  ;; first prep this dep by building its java classes
  (aborting-process {:command-args ["clojure" "-X:deps" "prep"]})
  (test-all opts)
  (bb/clean {:target "./target"})
  (let [root-target (canonicalise-file "target")]

    (doseq [module submodules]
      (build-submodule :mod-name module
                       :src-pom (canonicalise-file "template" (str module "-pom.xml"))
                       :lib (symbol "grafter" module)
                       :version version
                       :target root-target))))
