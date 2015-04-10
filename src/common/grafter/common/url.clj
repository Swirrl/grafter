(ns grafter.common.url
  (:require [clojure.string :as str]
            [grafter.rdf.io :refer [ISesameRDFConverter]])

  (:import [java.net URL URI]
           [org.openrdf.model.impl URIImpl]))

(defprotocol IURLable
  (->url [url]
    "Convert into a java.net.URL"))

(defprotocol IURIable
  (->uri [url]
    "Convert into a java.net.URI"))

(defprotocol IURL
  (set-host [url host])
  (host [url])
  (set-scheme [url scheme])
  (scheme [this])
  (set-port [this port])
  (port [this])
  (set-protocol [this protocol])
  (protocol [this])
  (set-url-fragment [url fragment])
  (url-fragment [url])
  (add-path-segments [url segments])
  (set-path-segments [url segments])
  (path-segments [url])
  (append-query-param [url key value]
    "Append the key and value to the query parameters")
  (set-query-params [url hash-map]
    "Adds the map of key value pairs to the query params.  Sorts the
    keys first to help provide guarantee's around URL equality.")
  (query-params [url])

  ;; Implementations should also implement IURLable and IURIable

  )

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

(defn build-sorted-params [hash-map]
  (->> hash-map
       (map (fn [[k v]] [(name k) (str v)]))
       (sort-by first)
       vec))

(extend-protocol IURL

  URI

  (set-host [url host]
    (URI. (protocol url) (.getUserInfo url) host (or (port url) -1) (.getPath url) (.getQuery url) (.getFragment url)))

  (host [url]
    (.getHost url))

  (set-port [url port]
    (URI. (protocol url) (.getUserInfo url) (host url) (or port -1) (.getPath url) (.getQuery url) (.getFragment url)))

  (port [url]
    (.getPort url))

  (set-protocol [url protocol]
    (URI. protocol (.getUserInfo url) (host url) (or (port url) -1) (.getPath url) (.getQuery url) (.getFragment url)))

  (protocol [this]
    (.getScheme this))

  (set-url-fragment [url fragment]
    (URI. (protocol url) (.getUserInfo url) (host url) (or (port url) -1) (.getPath url) (.getQuery url) (.getFragment fragment)))

  (url-fragment [url]
    (.getFragment url))

  (add-path-segments [url segments]
    (let [new-path (build-path segments)]
      (URI. (protocol url) (.getUserInfo url) (host url) (or (port url) -1) new-path (.getQuery url) (.getFragment url))))

  (set-path-segments [url segments]
    (URI. (protocol url) (.getUserInfo url) (host url) (or (port url) -1) (build-path segments) (.getQuery url) (.getFragment url)))

  (path-segments [url]
    (parse-path (.getPath url)))

  (append-query-param [url key value]
    (let [query-params (build-query-params (concat (query-params url) [[key value]]))]
      (URI. (protocol url) (.getUserInfo url) (host url) (or (port url) -1) (.getPath url) query-params (.getFragment url))))

  (set-query-params [url hash-map]
    (let [params (->> hash-map
                      build-sorted-params
                      build-query-params)]
      (URI. (protocol url) (.getUserInfo url) (host url) (or (port url) -1) (.getPath url) params (.getFragment url))))

  (query-params [url]
    (parse-query-params (.getQuery url)))

  (->url [url]
    (.toURL url))

  (->uri [url]
    url))

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
    (:assoc url :scheme scheme))

  (scheme [this]
    (:scheme this))

  (set-port [this port]
    (assoc :port port))

  (port [this]
    (:port this))

  (set-url-fragment [this fragment]
    (assoc this :url-fragment fragment))

  (url-fragment [this]
    (:url-fragment this))

  (add-path-segments [url segments]
    (update-in url [:path-segments] concat segments))

  (set-path-segments [url segments]
    (assoc :path-segments segments))

  (path-segments [url]
    (:path-segments url))

  (query-params [url]
    :query-params url)

  (append-query-param [url key value]
    (update-in url [:query-params] concat [[key value]]))

  (set-query-params [url hash-map]
    (let [kvs (build-sorted-params hash-map)]
      (assoc url :query-params kvs)))

  (->url [url]
    (to-uri* url))

  (->uri [url]
    (to-uri* url))

  Object
  (toString [this]
    (.toString (->uri this))))

(defmethod print-method GrafterURL [v ^java.io.Writer w]
  (.write w (str "#<GrafterURL " v ">")))

(defn url-builder
  "Parses a given string into a GrafterURL record. If the represented
  URI contains any reserved characters, they should be encoded
  correctly in the input string."
  [uri-str]
  (let [uri (if (instance? URI uri-str)
              uri-str
              (URI. uri-str))
        scheme (protocol uri)
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
