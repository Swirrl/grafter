(defproject grafter/grafter "2.1.18-SNAPSHOT"
  :description "Tools for the hard graft of linked data processing"
  :url "http://grafter.org/"
  :license {:name "Eclipse Public License - v1.0"
            :url "https://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git"
        :url "https://github.com/Swirrl/grafter"}

  :deploy-repositories [["releases" :clojars]]

  :dependencies [[org.clojure/clojure "1.10.3"]

                 ;;[org.eclipse.rdf4j/rdf4j-runtime "3.0.0" :exclusions [ch.qos.logback/logback-classic]]

                 ;; Include a smaller set of dependencies than we used
                 ;; to by default, if you want everything from RDF4j
                 ;; you can include:

                 ;; [org.eclipse.rdf4j/rdf4j-runtime "2.5.0" :exclusions [ch.qos.logback/logback-classic]]
                 [org.eclipse.rdf4j/rdf4j-rio-api "3.1.4"]
                 [org.eclipse.rdf4j/rdf4j-rio-binary "3.1.4"]
                 [org.eclipse.rdf4j/rdf4j-rio-jsonld "3.1.4"]
                 [org.eclipse.rdf4j/rdf4j-rio-n3 "3.1.4"]
                 [org.eclipse.rdf4j/rdf4j-rio-nquads "3.1.4"]
                 [org.eclipse.rdf4j/rdf4j-rio-rdfjson "3.1.4"]
                 [org.eclipse.rdf4j/rdf4j-rio-rdfxml "3.1.4"]
                 [org.eclipse.rdf4j/rdf4j-rio-trig "3.1.4"]
                 [org.eclipse.rdf4j/rdf4j-rio-trix "3.1.4"]
                 [org.eclipse.rdf4j/rdf4j-queryresultio-api "3.1.4"]
                 [org.eclipse.rdf4j/rdf4j-queryresultio-binary "3.1.4"]
                 [org.eclipse.rdf4j/rdf4j-queryresultio-binary "3.1.4"]
                 [org.eclipse.rdf4j/rdf4j-queryresultio-sparqljson "3.1.4"]
                 [org.eclipse.rdf4j/rdf4j-queryresultio-sparqlxml "3.1.4"]
                 [org.eclipse.rdf4j/rdf4j-queryresultio-text "3.1.4"]

                 [org.eclipse.rdf4j/rdf4j-repository-api "3.1.4"]
                 [org.eclipse.rdf4j/rdf4j-repository-http "3.1.4"]
                 [org.eclipse.rdf4j/rdf4j-repository-sail "3.1.4"]
                 [org.eclipse.rdf4j/rdf4j-repository-dataset "3.1.4"]
                 [org.eclipse.rdf4j/rdf4j-sail-memory "3.1.4"]
                 [org.eclipse.rdf4j/rdf4j-sail-inferencer "3.1.4"]
                 [org.eclipse.rdf4j/rdf4j-sail-nativerdf "3.1.4"]
                 [org.eclipse.rdf4j/rdf4j-repository-manager "3.1.4"]

                 [grafter/url "0.2.5"]
                 [grafter/vocabularies "0.3.5"] ;; also update this in shadow-cljs.edn
                 [me.raynes/fs "1.4.6" :exclusions [org.apache.commons/commons-compress]]
                 [potemkin "0.4.5"]]

  ;; Ensure we build the java sub project source code too!
  :java-source-paths ["src-java/grafter_sparql_repository/src/main/java"]

  :source-paths ["src" "deprecated/src"]
  :test-paths ["test"]

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
  :jvm-opts ["-Dapple.awt.UIElement=true" #_"--illegal-access=debug"]

  ;; Target JDK 8 expected JVM version
  :javac-options ["-target" "8" "-source" "8"]

  :pedantic? true

  :profiles { ;; expect upstream projects to provide this explicity if they want sesame
             :provided {:dependencies [[org.openrdf.sesame/sesame-runtime "2.8.9"]
                                       [org.clojure/tools.logging "0.4.0"]]}

             :dev [:provided
                   :dev-deps
                   :grafter-1-tests ;; test the deprecated grafter-1
                   ]

             :grafter-1-tests {:test-paths ["deprecated/test"] }

             :dev-deps {

                        :dependencies [[http-kit "2.3.0"]
                                       [org.slf4j/slf4j-simple "1.7.25"]
                                       [prismatic/schema "1.1.7"]
                                       [criterium "0.4.4"]
                                       [thheller/shadow-cljs "2.8.93"]]

                        :resource-paths ["dev/resources"]

                        :env {:dev true}}}

  :plugins [[lein-codox "0.10.6"]])
