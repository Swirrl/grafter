(defproject grafter/grafter "0.11.0.2-drafter-rdf4j"
  :description "Tools for the hard graft of linked data processing"
  :url "http://grafter.org/"
  :license {:name "Eclipse Public License - v1.0"
            :url "https://www.eclipse.org/legal/epl-v10.html"}

  :deploy-repositories [["releases" :clojars]]

  :dependencies [[org.clojure/clojure "1.9.0-beta4"]
                 [org.eclipse.rdf4j/rdf4j-runtime "2.2.3"]
                 [org.clojure/tools.logging "0.4.0"]
                 [grafter/url "0.2.5"]
                 [grafter/vocabularies "0.2.2"]
                 [me.raynes/fs "1.4.6"]
                 [potemkin "0.4.4"]
                 [com.novemberain/pantomime "2.9.0"]] ;; mimetypes

  :source-paths ["src" #_"deprecated/src"]

  :codox {:defaults {:doc "FIXME: write docs"
                     :doc/format :markdown}
          :output-dir "api-docs"
          :sources ["src" ;; Include docs from grafter-url project too
                    "../grafter-url/src"]

          ;; TODO change this when we merge back to master
          :src-dir-uri "http://github.com/Swirrl/grafter/blob/0.8.x-SNAPSHOT/"
          :src-linenum-anchor-prefix "L"}


  ;; Prevent Java process from appearing as a GUI app in OSX when
  ;; Swing classes are loaded.
  :jvm-opts ["-Dapple.awt.UIElement=true"]

  :pedantic? true

  :profiles {:clj-19 { :dependencies [[org.clojure/clojure "1.9.0-alpha14"]] }

             ;; expect upstream projects to provide this explicity if they want sesame
             :provided {:dependencies [[org.openrdf.sesame/sesame-runtime "2.8.9"]]}
             
             :dev [:provided :dev-deps]

             :dev-deps {:plugins [[codox "0.8.10"]]

                        :dependencies [[org.slf4j/slf4j-simple "1.7.25"]
                                       [prismatic/schema "1.1.7"]
                                       [criterium "0.4.4"]]

                        :resource-paths ["dev/resources"]

                        :env {:dev true}}})
