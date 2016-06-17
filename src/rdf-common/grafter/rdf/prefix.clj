(ns rdf-common.grafter.rdf.prefix
  (:require [grafter.rdf.io :refer [default-prefixes]]
            [grafter.rdf :refer [subject predicate object context]]
            [grafter.url :refer [path-segments url-fragment scheme]]))


(defn parse-prefix [uri]
  (if (url-fragment uri)
    (last (path-segments uri))
    (last (butlast (path-segments uri)))))

(defn find-prefixes [quads]
  (->> quads
       (mapcat (fn [quad]
                 [(subject quad) (predicate quad) (object quad)]))
       (filter #(and (instance? java.net.URI %)
                               (#{"http" "https"} (scheme %))))
       #_parse-prefix
       #_set

       ))
