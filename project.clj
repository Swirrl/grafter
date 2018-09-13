(defproject grafter/grafter "0.11.5-SNAPSHOT"
  :description "Tools for the hard graft of linked data processing"
  :url "http://grafter.org/"
  :license {:name "Eclipse Public License - v1.0"
            :url "https://www.eclipse.org/legal/epl-v10.html"}

  :deploy-repositories [["releases" :clojars]]

  :dependencies [[org.clojure/clojure "1.9.0-RC1"]
                 [org.openrdf.sesame/sesame-runtime "2.8.9"]
                 [org.clojure/tools.logging "0.3.1"]
                 [grafter/url "0.2.5"]
                 [grafter/vocabularies "0.2.4-SNAPSHOT"]

                 [commons-logging "1.2"] ;; Shouldn't need this, but somehow excluded and required by SPARQLRepository
                 [me.raynes/fs "1.4.6"]
                 [potemkin "0.4.3"]
                 [com.novemberain/pantomime "2.8.0"]] ;; mimetypes


  :codox {:defaults {:doc "FIXME: write docs"
                     :doc/format :markdown}
          :output-path "api-docs"
          :sources ["src" ;; Include docs from grafter-url project too
                    "../grafter-url/src"]

          ;; TODO change this when we merge back to master
          :src-dir-uri "http://github.com/Swirrl/grafter/blob/0.8.x-SNAPSHOT/"
          :src-linenum-anchor-prefix "L"}

  ;; Prevent Java process from appearing as a GUI app in OSX when
  ;; Swing classes are loaded.
  :jvm-opts ["-Dapple.awt.UIElement=true"]

  :pedantic? true

  :plugins [[codox "0.8.10"]
            [lein-doo "0.1.10"]]

  :profiles {

             :dev {:dependencies [[org.clojure/clojurescript "1.9.946"]
                                  [org.slf4j/slf4j-simple "1.7.21"]
                                  [prismatic/schema "1.1.3"]
                                  [criterium "0.4.4"]]

                   :resource-paths ["dev/resources"]

                   :env {:dev true}}}


  :cljsbuild
  {:builds [{:id "test-nashorn"
             :source-paths ["src" "test"]
             :compiler {:output-to  "target/cljs/test-nashorn/test-grafter.js"
                        :output-dir "target/cljs/test-nashorn/out"
                        :main grafter.test-runner
                        :optimizations :simple
                        :process-shim false}}
            {:id "test-node"
             :source-paths ["src" "test"]
             :compiler {:target :nodejs
                        :output-to  "target/cljs/test-node/test-grafter.js"
                        :output-dir "target/cljs/test-node/out"
                        :main grafter.test-runner
                        :optimizations :none
                        :process-shim false}}]}

  :aliases {"test-nashorn" ["doo" "nashorn" "test-nashorn" "once"]
            "test-node"    ["doo" "node" "test-node" "once"]
            "test-cljs"    ["do" ["test-nashorn"] ["test-node"]]
            "test-all"     ["do" ["test"] ["test-cljs"]]
            })
