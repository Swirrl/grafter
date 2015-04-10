(ns grafter.common.url
  (:require [clojure.string :as str]
            [grafter.rdf.io :refer [ISesameRDFConverter]])

  (:import [java.net URL URI]
           [org.openrdf.model.impl URIImpl]))


(defprotocol IURLable
  (->java-url [url]
    "Convert into a URL"))

(defprotocol IURIable
  (->java-uri [url]
    "Convert into a java.net.URI"))

(defprotocol IURL
  "A protocol for manipulating URL objects.  Implementations of this
  protocol should also implement the protocols IURLable and IURIable"

  (set-host [url host]
    "Set the host domain of the URL.")

  (host [url]
    "Get the host domain of the URL.")

  (set-scheme [url scheme]
    "Set the URL scheme e.g. http, https.")

  (scheme [this]
    "Get the URL scheme.")

  (set-port [this port]
    "Set the port of the URL.")

  (port [this]
    "Get the port of the URL.")

  (set-url-fragment [url fragment]
    "Set the URL #fragment")

  (url-fragment [url]
    "Get the URL fragment from the URL.")

  (add-path-segments [url segments]
    "Append new path segments to the URL path.")

  (set-path-segments [url segments]
    "Set the path segments to those supplied.")

  (path-segments [url]
    "Get the path segments for the URL.")

  (append-query-param [url key value]
    "Append the key and value to the query parameters")

  (set-query-params [url hash-map]
    "Adds the map of key value pairs to the query params.  Sorts the
    keys first to help provide guarantee's around URL equality.")

  (query-params [url]
    "Return the query parameters for the URL as an ordered sequence of
    key/value tuples.")

  (query-params-map [url]
    "Return the query parameters for the URL as a hash-map if there
    are multiple occurrences of the same parameter the last occurrence
    wins."))

(defn parse-path [path-str]
  (when-not (#{nil ""} path-str)
    (remove #{""} (str/split path-str #"/"))))

(defn- build-path [path-segments]
  (cond
    (nil? path-segments) nil
    (empty? path-segments) "/"
    :else (str "/" (str/join "/" path-segments))))

(defn- join-paths [url new-segments]
  (if (empty? new-segments)
    (.getPath url)
    (let [old-path (parse-path (.getPath url))]
      (str "/" (str/join "/" (concat old-path new-segments))))))

(defn- parse-query-params [uri-params]
  "Parses a query parameter string of the form
  param=value&param2=value2"
  (when uri-params (mapv #(str/split % #"=") (-> uri-params
                                                 (str/split #"&")))))

(defn- build-query-params [kvs]
  (->> kvs
       (map (fn [[k v]] (str k "=" v)))
       (str/join "&")))

(defn- build-sorted-params [hash-map]
  (->> hash-map
       (map (fn [[k v]] [(name k) (str v)]))
       (sort-by first)
       vec))

(defn- build-sorted-query-params [hash-map]
  (->> hash-map
       build-sorted-params
       build-query-params))

(extend-type URI

  IURL

  (set-host [url host]
    (URI. (scheme url) (.getUserInfo url) host (or (port url) -1) (.getPath url) (.getQuery url) (.getFragment url)))

  (host [url]
    (.getHost url))

  (set-port [url port]
    (URI. (scheme url) (.getUserInfo url) (host url) (or port -1) (.getPath url) (.getQuery url) (.getFragment url)))

  (port [url]
    (.getPort url))

  (set-scheme [url protocol]
    (URI. protocol (.getUserInfo url) (host url) (or (port url) -1) (.getPath url) (.getQuery url) (.getFragment url)))

  (scheme [this]
    (.getScheme this))

  (set-url-fragment [url fragment]
    (URI. (scheme url) (.getUserInfo url) (host url) (or (port url) -1) (.getPath url) (.getQuery url) fragment))

  (url-fragment [url]
    (.getFragment url))

  (add-path-segments [url segments]
    (let [new-path (build-path segments)]
      (URI. (scheme url) (.getUserInfo url) (host url) (or (port url) -1) new-path (.getQuery url) (.getFragment url))))

  (set-path-segments [url segments]
    (URI. (scheme url) (.getUserInfo url) (host url) (or (port url) -1) (build-path segments) (.getQuery url) (.getFragment url)))

  (path-segments [url]
    (parse-path (.getPath url)))

  (append-query-param [url key value]
    (let [query-params (build-query-params (concat (query-params url) [[key value]]))]
      (URI. (scheme url) (.getUserInfo url) (host url) (or (port url) -1) (.getPath url) query-params (.getFragment url))))

  (set-query-params [url hash-map]
    (let [params (build-sorted-query-params hash-map)]
      (URI. (scheme url) (.getUserInfo url) (host url) (or (port url) -1) (.getPath url) params (.getFragment url))))

  (query-params [url]
    (parse-query-params (.getQuery url)))

  IURLable

  (->java-url [url]
    (.toURL url))

  IURIable
  (->java-uri [url]
    url))

(defn- url-end
  "Build the tail end of a URL (the file path + query string +
  fragment)"
  [url]

  (if-let [fragment (.getRef url)]
    (str (.getFile url) "#" fragment)
    (.getFile url)))

(defn- url-end-path-fragment [url]
  (if-let [fragment (.getRef url)]
    (str (.getPath url) "#" fragment)
    (.getPath url)))

(extend-type URL

  IURL

  (set-host [url host]
    (URL. (scheme url) host (or (port url) -1) (url-end url)))

  (host [url]
    (.getHost url))

  (set-scheme [url scheme]
    (URL. scheme (host url) (or (port url) -1) (url-end url)))

  (scheme [this]
    (.getProtocol this))

  (set-port [url port]
    (let [p (or port -1)
          port (if (instance? String p)
                 (Integer/parseInt p)
                 p)]
      (URL. (scheme url) (host url) port (url-end url))))

  (port [this]
    (let [port (.getPort this)]
      (when-not (= -1 port)
        port)))

  (set-url-fragment [url fragment]
    (if fragment
      (URL. (scheme url) (host url) (or (port url) -1) (str (.getFile url) "#" fragment))
      (URL. (scheme url) (host url) (or (port url) -1) (str (.getFile url)))))

  (url-fragment [url]
    (.getRef url))

  (add-path-segments [url segments]
    (set-path-segments url
                       (parse-path (join-paths url segments))))

  (set-path-segments [url segments]
    (let [path (build-path segments)
          file (if-let [qp (query-params url)]
                 (str path "?" qp)
                 path)
          file-frag (if-let [fragment (url-fragment url)]
                      (str file "#" fragment)
                      file)]
      (URL. (scheme url) (host url) (or (port url) -1) file-frag)))

  (path-segments [url]
    (parse-path (.getPath url)))

  (append-query-param [url key value]
    (let [query-params (build-query-params (concat (query-params url) [[key value]]))]
      (if-let [fragment (.getRef url)]
        (URL. (scheme url) (host url) (or (port url) -1) (str (.getPath url) "?" query-params "#" fragment))
        (URL. (scheme url) (host url) (or (port url) -1) (str (.getPath url) "?" query-params)))))

  (set-query-params [url hash-map]
    (URL. (scheme url) (host url) (or (port url) -1)
          (if hash-map
            (let [file (str (.getPath url) "?" (build-sorted-query-params hash-map))]
              (if-let [fragment (url-fragment url)]
                (str file "#" fragment)
                file))
            (url-end-path-fragment url))))

  (query-params [url]
    (parse-query-params (.getQuery url))))

(defn- append-to [url key values]
  (update-in url [:path-segments] concat [[key values]]))

(defn- to-uri*
  "Converts a map of URI components into a java.net.URI. The supported components are:
   scheme: URI scheme e.g. http, file
   host: Host name or address
   path-segments: A sequence of path segments - these will be concatenated together to form the path of the resulting URI
   query-params: A sequence of key-value pairs to combine into a query string of the form key1=value1&key2=value2..."
  ^URI [{:keys [scheme host port path-segments query-params url-fragment]}]
  (let [path (build-path path-segments)
        query (build-query-params query-params)]
    (URI. scheme nil host port path (not-empty query) url-fragment)))

(defrecord GrafterURL [scheme host port path-segments query-params url-fragment]
  IURL

  (set-host [url host]
    (assoc url :host host))

  (host [this]
    (:host this))

  (set-scheme [url scheme]
    (assoc url :scheme scheme))

  (scheme [this]
    (:scheme this))

  (set-port [this port]
    (assoc this :port port))

  (port [this]
    (:port this))

  (set-url-fragment [this fragment]
    (assoc this :url-fragment fragment))

  (url-fragment [this]
    (:url-fragment this))

  (add-path-segments [url segments]
    (update-in url [:path-segments] concat segments))

  (set-path-segments [url segments]
    (assoc url :path-segments segments))

  (path-segments [url]
    (:path-segments url))

  (query-params [url]
    (:query-params url))

  (append-query-param [url key value]
    (update-in url [:query-params] concat [[key value]]))

  (set-query-params [url hash-map]
    (let [kvs (build-sorted-params hash-map)]
      (assoc url :query-params kvs)))

  IURLable
  (->java-url [url]
    (to-uri* url))

  IURIable
  (->java-uri [url]
    (to-uri* url))

  Object
  (toString [this]
    (.toString (->java-uri this))))

(defn query-params-map
  "Returns a map of query parameters from a URL query string.  If
  there are duplicate keys, the last occurrence of each duplicate
  parameter wins.

  e.g. with the following query parameters ?foo=1&foo=2&bar=3 the map
  {\"foo\" 2 \"bar\" 3} is returned."
  [url]
  (->> url
       query-params
       flatten
       (apply hash-map)))

(defmethod print-method GrafterURL [v ^java.io.Writer w]
  (.write w (str "#<GrafterURL " v ">")))

(defn ->url
  "Parses a given string into a GrafterURL record.  If the represented
  URI contains any reserved characters, they should be encoded
  correctly in the input string."
  [uri-str]
  (let [uri (if (instance? URI uri-str)
              uri-str
              (URI. uri-str))
        scheme (scheme uri)
        host (host uri)
        port (port uri)
        fragment (url-fragment uri)
        qparams (query-params uri)
        path-segments (path-segments uri)]
    (->GrafterURL scheme host port path-segments qparams fragment)))

(extend-protocol ISesameRDFConverter
  GrafterURL

  (sesame-rdf-type->type [uri]
    (url-builder (str uri)))

  (->sesame-rdf-type [uri]
    (URIImpl. (str uri))))
