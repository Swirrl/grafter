(defproject grafter/grafter "2.0.0"
  :description "Tools for the hard graft of linked data processing"
  :url "http://grafter.org/"
  :license {:name "Eclipse Public License - v1.0"
            :url "https://www.eclipse.org/legal/epl-v10.html"}

  :deploy-repositories [["releases" :clojars]]

  :dependencies [[org.clojure/clojure "1.10.0"]

                 ;;[org.eclipse.rdf4j/rdf4j-runtime "2.5.0" :exclusions [ch.qos.logback/logback-classic]]

                 ;; Include a smaller set of dependencies than we used
                 ;; to by default, if you want everything from RDF4j
                 ;; you can include:

                 ;; [org.eclipse.rdf4j/rdf4j-runtime "2.5.0" :exclusions [ch.qos.logback/logback-classic]]
                 [org.eclipse.rdf4j/rdf4j-rio-api "2.5.0"]
                 [org.eclipse.rdf4j/rdf4j-rio-binary "2.5.0"]
                 [org.eclipse.rdf4j/rdf4j-rio-jsonld "2.5.0"]
                 [org.eclipse.rdf4j/rdf4j-rio-n3 "2.5.0"]
                 [org.eclipse.rdf4j/rdf4j-rio-nquads "2.5.0"]
                 [org.eclipse.rdf4j/rdf4j-rio-rdfjson "2.5.0"]
                 [org.eclipse.rdf4j/rdf4j-rio-rdfxml "2.5.0"]
                 [org.eclipse.rdf4j/rdf4j-rio-trig "2.5.0"]
                 [org.eclipse.rdf4j/rdf4j-rio-trix "2.5.0"]
                 [org.eclipse.rdf4j/rdf4j-queryresultio-api "2.5.0"]
                 [org.eclipse.rdf4j/rdf4j-queryresultio-binary "2.5.0"]
                 [org.eclipse.rdf4j/rdf4j-queryresultio-binary "2.5.0"]
                 [org.eclipse.rdf4j/rdf4j-queryresultio-sparqljson "2.5.0"]
                 [org.eclipse.rdf4j/rdf4j-queryresultio-sparqlxml "2.5.0"]
                 [org.eclipse.rdf4j/rdf4j-queryresultio-text "2.5.0"]

                 [org.eclipse.rdf4j/rdf4j-repository-api "2.5.0"]
                 [org.eclipse.rdf4j/rdf4j-repository-http "2.5.0"]
                 [org.eclipse.rdf4j/rdf4j-repository-sail "2.5.0"]
                 [org.eclipse.rdf4j/rdf4j-repository-dataset "2.5.0"]
                 [org.eclipse.rdf4j/rdf4j-sail-memory "2.5.0"]
                 [org.eclipse.rdf4j/rdf4j-sail-nativerdf "2.5.0"]
                 [org.eclipse.rdf4j/rdf4j-repository-manager "2.5.0"]

                 [grafter/url "0.2.5"]
                 [grafter/vocabularies "0.2.6"]
                 [me.raynes/fs "1.4.6"]
                 [potemkin "0.4.5"]]

  :source-paths ["src" "deprecated/src"]
  :test-paths ["test" "deprecated/test"]

  :codox {:defaults {:doc "FIXME: write docs"
                     :doc/format :markdown}
          :output-path "api-docs"
          :sources ["src" ;; Include docs from grafter-url project too
                    "deprecated/src"
                    "../grafter-url/src"]

          ;; TODO change this when we merge back to master
          :source-uri "http://github.com/Swirrl/grafter/blob/rdf-core/{filepath}#L{line}"

          }


  ;; Prevent Java process from appearing as a GUI app in OSX when
  ;; Swing classes are loaded.
  :jvm-opts ["-Dapple.awt.UIElement=true"]

  :pedantic? true

  :profiles { ;; expect upstream projects to provide this explicity if they want sesame
             :provided {:dependencies [[org.openrdf.sesame/sesame-runtime "2.8.9"]
                                       [org.clojure/tools.logging "0.4.0"]]}

             :dev [:provided :dev-deps]


             :dev-deps {

                        :dependencies [
                                       [org.slf4j/slf4j-simple "1.7.25"]
                                       [prismatic/schema "1.1.7"]
                                       [criterium "0.4.4"]]

                        :resource-paths ["dev/resources"]

                        :env {:dev true}}}

  :plugins [[lein-codox "0.10.6"]])
