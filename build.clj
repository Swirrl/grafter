(ns build
  (:require [clojure.tools.build.api :as b]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [deps-deploy.deps-deploy :as dd]
            ))

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
  `(binding [b/*project-root* ~dir] ~@forms))

(defn- build-submodule [& {:keys [jar-file mod-name lib pom root-target] :as opts}]
  (assert (and lib version mod-name) "mod-name, lib and version are required for jar")

  (println "Building submodule " mod-name)
  (let [basis (b/create-basis {:project "deps.edn"
                               :aliases [:overrides]
                               :extra {:aliases {:overrides
                                                 {:override-deps
                                                  {'io.github.swirrl/grafter.core {:mvn/version version}
                                                   'io.github.swirrl/grafter.io {:mvn/version version}
                                                   'io.github.swirrl/grafter.repository {:mvn/version version}}}}}})

        mod-target (str (io/file root-target mod-name))
        jar-file (str (io/file root-target jar-file))]

    (b/write-pom (assoc opts
                        :basis basis
                        :class-dir mod-target))

    (b/copy-dir {:src-dirs   (:paths basis)
                 :target-dir mod-target})

    (println "Building jar" (str jar-file "..."))

    (b/jar (assoc opts
                  :class-dir mod-target
                  :jar-file jar-file))

    ;;(println "Installing " jar-file " " mod-target)
    (b/install (assoc opts
                      :basis basis
                      :jar-file jar-file
                      :class-dir mod-target
                      ))))

(def submodules ["grafter.core" "grafter.io" "grafter.repository"])

(defn- canonicalise-file [& f]
  (str (apply io/file (.getCanonicalFile (io/file (or b/*project-root* ".")))
              f)))

(defn build-grafter-repo [opts]
  (with-project-root "grafter.repository"
    (b/process {:command-args ["clojure" "-T:build" "compile-java"]})))

(defn clean-all [opts]
  (b/delete {:path "./target"})
  (b/delete {:path "./grafter.core/target"})
  (b/delete {:path "./grafter.repository/target"}))

(defn build-all-modules [opts]
  (let [root-target (canonicalise-file "target")]
    (doseq [module submodules]
      (with-project-root module
        (build-submodule :mod-name module
                         :jar-file (format "%s-%s.jar" module version)
                         :src-pom (canonicalise-file "template" (str module "-pom.xml"))
                         :module-target (canonicalise-file module "target")
                         :lib (symbol "io.github.swirrl" module)
                         :version version
                         :root-target root-target)))))

(defn build-all
  "Build all submodules locally"
  [opts]
  ;; first prep this dep by building its java classes
  (aborting-process {:command-args ["clojure" "-X:deps" "prep"]}) ;; TODO is this necessary?

  (clean-all opts)
  (build-grafter-repo opts)
  (test-all opts)

  (build-all-modules opts))


(defn deploy-all [opts]
  (doseq [module submodules]
    (dd/deploy {:artifact (format "./target/%s-%s.jar" module version)
                :installer :remote
                :sign-releases? false ;; TODO
                :pom-file (format "./target/%s/META-INF/maven/io.github.swirrl/%s/pom.xml" module module)})))

(defn ci-deploy
  "Task to deploy tagged commits to clojars"
  [opts]
  (let [circle-tag (System/getenv "CIRCLE_TAG")]
    (if (some-> circle-tag (str/starts-with? "v"))
      (deploy-all opts)
      (println "Skipping clojars deployment (not a TAG)"))))

(comment

  (clean-all {})

  (build-grafter-repo {})

  (build-all {})

  (build-all-modules {})

  (do (clean-all {})
      (build-grafter-repo {})
      (let [module "grafter.repository"
            root-target (canonicalise-file "target")]
        (build-submodule :mod-name module
                         :src-pom (canonicalise-file "template" (str module "-pom.xml"))
                         :module-target (canonicalise-file module "target")
                         :lib (symbol "grafter" module)
                         :version version
                         :root-target root-target)))



  #_(with-project-root "grafter.io"
      (let [basis (b/create-basis {:project (-> (edn/read-string (slurp "grafter.io/deps.edn"))
                                              (update-in [:deps 'grafter/grafter.core] (constantly {:mvn/version version})))})])))
