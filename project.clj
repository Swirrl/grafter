(defproject grafter/grafter "0.10.2-SNAPSHOT"
  :description "Tools for the hard graft of linked data processing"
  :url "http://grafter.org/"
  :license {:name "Eclipse Public License - v1.0"
            :url "https://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git"
        :url "https://github.com/Swirrl/grafter"}

  :deploy-repositories [["releases" :clojars]]

  :dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                 [org.eclipse.rdf4j/rdf4j-runtime "2.2.2"]
                 [org.clojure/tools.logging "0.4.0"]
                 [grafter/url "0.2.5"]
                 [grafter/vocabularies "0.2.2"]

                 [me.raynes/fs "1.4.6"]
                 [potemkin "0.4.4"]
                 [com.novemberain/pantomime "2.9.0"]] ;; mimetypes


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

             :dev {:plugins [[codox "0.8.10"]]

                   :dependencies [[org.slf4j/slf4j-simple "1.7.25"]
                                  [prismatic/schema "1.1.7"]
                                  [criterium "0.4.4"]]

                   :resource-paths ["dev/resources"]

                   :env {:dev true}}})
