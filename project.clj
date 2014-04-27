(defproject grafter "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :repositories [["geotools" "http://download.osgeo.org/webdav/geotools/"]]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 ;;[org.apache.jena/jena-core "2.11.1"]
                 ;;[plaza "0.0.5-SNAPSHOT"]
                 [org.clojure/algo.monads "0.1.5"]
                 [clj-time "0.7.0"]
                 [org.clojure/tools.namespace "0.2.4"]
                 [org.clojure/math.combinatorics "0.0.7"]
                 [clojure-csv/clojure-csv "2.0.1"]
                 [org.marianoguerra/clj-rhino "0.2.1"]
                 [org.openrdf.sesame/sesame-runtime "2.7.10"]
                 [org.geotools/gt-main "2.7-M3"]
                 [org.geotools/gt-swing "2.7-M3"]
                 [org.geotools/gt-shapefile "2.7-M3"]
                 [seesaw "1.4.4"]]
  :source-paths ["src"]
  :java-source-paths ["src-java"]
  )
