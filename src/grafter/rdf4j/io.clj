(ns grafter.rdf4j.io
  "Functions & Protocols for serializing Grafter Statements to (and from)
  any Linked Data format supported by RDF4j."
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [grafter.rdf4j
             [formats :as fmt]]
            [grafter
             [core :as pr :refer [->Quad ->grafter-type IGrafterRDFType]]]
            [grafter.url
             :refer
             [->grafter-url ->java-uri ->url IURIable ToGrafterURL]])
  (:import [grafter.core IStatement Quad RDFLiteral LangString]
           grafter.url.GrafterURL
           java.io.File
           [java.net MalformedURLException URL]
           java.util.GregorianCalendar
           javax.xml.datatype.DatatypeFactory
           [org.eclipse.rdf4j.model BNode Literal Resource Statement URI Value]
           [org.eclipse.rdf4j.model.impl SimpleValueFactory BNodeImpl BooleanLiteralImpl CalendarLiteral ContextStatementImpl IntegerLiteral LiteralImpl NumericLiteral StatementImpl URIImpl]
           [org.eclipse.rdf4j.rio RDFFormat RDFHandler RDFWriter Rio]))

(extend-type Statement
  ;; Extend our IStatement protocol to Sesame's Statements for convenience.
  pr/IStatement
  (subject [this] (.getSubject this))
  (predicate [this] (.getPredicate this))
  (object [this] (.getObject this))
  (context [this] (.getContext this)))

(defmethod pr/blank-node? BNode [_]
  true)

(defprotocol IRDF4jConverter
  (->backend-type [this] "Convert an arbitrary statement type into an RDF4j Statement type"))

(defmulti backend-literal->grafter-type
  "A multimethod to convert a backend RDF literal into a corresponding
  Clojure type.  This method can be extended to provide custom
  conversions. You should typically not need to call this directly,
  instead see backend-quad->grafter-quad."
  (fn [lit]
    (when-let [datatype (pr/datatype-uri lit)]
      (str datatype))))

(defmethod backend-literal->grafter-type nil [literal]
  (pr/language (pr/raw-value literal) (pr/lang literal)))

(defmethod backend-literal->grafter-type "http://www.w3.org/2001/XMLSchema#boolean" [literal]
  (Boolean/parseBoolean (pr/raw-value literal)))

(defmethod backend-literal->grafter-type "http://www.w3.org/2001/XMLSchema#byte" [literal]
  (Byte/parseByte (pr/raw-value literal)))

(defmethod backend-literal->grafter-type "http://www.w3.org/2001/XMLSchema#short" [literal]
  (Short/parseShort (pr/raw-value literal)))

(defmethod backend-literal->grafter-type "http://www.w3.org/2001/XMLSchema#decimal" [literal]
  ;; Prefer clj's big integer over java's because of hash code issue:
  ;; http://stackoverflow.com/questions/18021902/use-cases-for-bigint-versus-biginteger-in-clojure
  (bigdec (pr/raw-value literal)))

(defmethod backend-literal->grafter-type "http://www.w3.org/2001/XMLSchema#double" [literal]
  (Double/parseDouble (pr/raw-value literal)))

(defmethod backend-literal->grafter-type "http://www.w3.org/2001/XMLSchema#float" [literal]
  (Float/parseFloat (pr/raw-value literal)))

(defmethod backend-literal->grafter-type "http://www.w3.org/2001/XMLSchema#integer" [literal]
  (bigint (pr/raw-value literal)))

(defmethod backend-literal->grafter-type "http://www.w3.org/2001/XMLSchema#int" [literal]
  (java.lang.Integer/parseInt (pr/raw-value literal)))

(defmethod backend-literal->grafter-type "http://www.w3.org/2001/XMLSchema#long" [literal]
  (java.lang.Long/parseLong (pr/raw-value literal)))

(defmethod backend-literal->grafter-type "http://www.w3.org/TR/xmlschema11-2/#string" [literal]
  (pr/language (pr/raw-value literal) (pr/lang literal)))

(defmethod backend-literal->grafter-type "http://www.w3.org/2001/XMLSchema#string" [literal]
  (pr/raw-value literal))

(defmethod backend-literal->grafter-type "http://www.w3.org/1999/02/22-rdf-syntax-ns#langString" [literal]
  (pr/language (pr/raw-value literal) (pr/lang literal)))

(defmethod backend-literal->grafter-type "http://www.w3.org/2001/XMLSchema#dateTime" [literal]
  (java.sql.Time. (-> literal .calendarValue .toGregorianCalendar .getTime .getTime)))

(defmethod backend-literal->grafter-type "http://www.w3.org/2001/XMLSchema#date" [literal]
  (-> literal .calendarValue .toGregorianCalendar .getTime))

(defmethod backend-literal->grafter-type :default [literal]
  ;; If we don't have a type conversion for it, let the RDF4j type
  ;; through, as it's not really up to grafter to fail the processing,
  ;; as they might just want to pass data through rather than
  ;; understand it.
  literal)

(defn quad->backend-quad
  "Convert a grafter IStatement into a backend (RDF4j) statement type."
  [^IStatement is]
  (try
    (if (pr/context is)
      (ContextStatementImpl. (->backend-type (pr/subject is))
                             (->backend-type (pr/predicate is))
                             (->backend-type (pr/object is))
                             (->backend-type (pr/context is)))
      (StatementImpl. (->backend-type (pr/subject is))
                      (->backend-type (pr/predicate is))
                      (->backend-type (pr/object is))))
    (catch ClassCastException cce
      ;; We could really make do with just letting the ClassCastException raise,
      ;; but improve the message a little to nudge developers in the right
      ;; direction, about what is likely to be wrong.
      (throw (ex-info "Error outputing Quad.  It looks like you have an incorrect data type inside a quad.  Check your URI's are not strings."
                      {:error :statement-conversion-error
                       :quad is
                       :quad-meta (meta is)} cce)))
    (catch Exception ex
      (throw (ex-info "Error outputing Quad" {:error :statement-conversion-error
                                              :quad is
                                              :quad-meta (meta is)} ex)))))

(extend-protocol IRDF4jConverter
  ;; Numeric Types

  java.lang.Byte
  (->backend-type [this]
    (.. (SimpleValueFactory/getInstance) (createLiteral this)))

  java.lang.Short
  (->backend-type [this]
    (.. (SimpleValueFactory/getInstance) (createLiteral this)))

  java.math.BigDecimal
  (->backend-type [this]
    (.. (SimpleValueFactory/getInstance) (createLiteral this)))

  java.lang.Double
  (->backend-type [this]
    (.. (SimpleValueFactory/getInstance) (createLiteral this)))

  java.lang.Float
  (->backend-type [this]
    (.. (SimpleValueFactory/getInstance) (createLiteral this)))

  java.lang.Integer
  (->backend-type [this]
    (.. (SimpleValueFactory/getInstance) (createLiteral (int this))))

  java.math.BigInteger
  (->backend-type [this]
    (.. (SimpleValueFactory/getInstance) (createLiteral this)))

  java.lang.Long
  (->backend-type [this]
    (.. (SimpleValueFactory/getInstance) (createLiteral (long this))))

  clojure.lang.BigInt
  (->backend-type [this]
    (.. (SimpleValueFactory/getInstance) (createLiteral  (BigInteger. (str this))))))

(extend-protocol IRDF4jConverter
  ;; Non numeric types

  Boolean
  (->backend-type [this]
    (.. (SimpleValueFactory/getInstance) (createLiteral this)))
  
  LangString
  (->backend-type [this]
    (.. (SimpleValueFactory/getInstance) (createLiteral (:string this) (name (:lang this)))))
  
  String
  (->backend-type [this]
    (.. (SimpleValueFactory/getInstance) (createLiteral this)))
  
  java.net.URL
  (->backend-type [this]
    (URIImpl. (str this)))
  
  java.net.URI
  (->backend-type [this]
    (URIImpl. (str this)))

  java.util.Date
  (->backend-type [this]
    (.. (SimpleValueFactory/getInstance) (createLiteral this)))
  
  Quad
  (->backend-type [this]
    (quad->backend-quad this))

  URI
  (->backend-type [this]
    this)

  Literal
  (->backend-type [this]
    this)

  Statement
  (->backend-type [this]
    this)

  clojure.lang.Keyword
  (->backend-type [this]
    (BNodeImpl. (name this)))
  
  RDFLiteral
  (->backend-type [this]
    (LiteralImpl. (pr/raw-value this) (URIImpl. (str (pr/datatype-uri this))))))


(extend-protocol IGrafterRDFType
  java.lang.Boolean
  (->grafter-type [this]
    this)

  Statement
  (->grafter-type [this]
    this)

  Literal
  (->grafter-type [this]
    (backend-literal->grafter-type this))

  URI
  (->grafter-type [this]
    (->java-uri this))

  java.net.URI
  (->grafter-type [this]
    this)

  BNode
  (->grafter-type [this]
    (-> this .getID keyword))

  BNodeImpl
  (->grafter-type [this]
    (-> this .getID keyword))

  java.util.Date
  (->grafter-type [this]
    this)

  clojure.lang.Keyword
  (->grafter-type [this]
    this)

  String
  ;; Assume URI's are the norm not strings
  (->grafter-type [this]
    this)

  LangString
  (->grafter-type [t]
    t))

(extend-type GrafterURL
  IGrafterRDFType
  (->grafter-type [uri]
    (->url (str uri)))
  
  IRDF4jConverter
  (->backend-type [uri]
    (URIImpl. (str uri))))

(defn backend-quad->grafter-quad
  "Convert an RDF4j backend quad into a grafter Quad."
  [^Statement st]
  ;; TODO fix this to work properly with object & context.
  ;; context should return either nil or a URI
  ;; object should be converted to a clojure type.
  (->Quad (->grafter-type (.getSubject st))
          (->java-uri (.getPredicate st))
          (->grafter-type (.getObject st))
          (when-let [graph (.getContext st)]
            (->grafter-type graph))))

(defn- resolve-format-preference
  "Takes an clojure.java.io destination (e.g. URL/File etc...) and a
  format-preference and tries to resolve them in a fallback chain.

  If format-preference does not resolve then we fallback to the destination's
  file extension if there is one. If no format can be resolved we raise an
  exception.

  format-preference can be a keyword e.g. :ttl, a string of an extension e.g
  \"nt\" or a mime-type."
  [dest format-preference]
  (if-let [fmt (or (fmt/->rdf-format format-preference)
                   (fmt/->rdf-format dest))]
         fmt
         (throw (ex-info "Could not infer file format, please supply a :format parameter" {:error :could-not-infer-file-format :object dest}))))

(def default-prefixes "A default set of common prefixes"
  {
   "dcat" "http://www.w3.org/ns/dcat#"
   "dcterms" "http://purl.org/dc/terms/"
   "owl" "http://www.w3.org/2002/07/owl#"
   "qb" "http://purl.org/linked-data/cube#"
   "rdf" "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
   "rdfs" "http://www.w3.org/2000/01/rdf-schema#"
   "sdmx-attribute" "http://purl.org/linked-data/sdmx/2009/attribute#"
   "sdmx-concept" "http://purl.org/linked-data/sdmx/2009/concept#"
   "sdmx-dimension" "http://purl.org/linked-data/sdmx/2009/dimension#"
   "skos" "http://www.w3.org/2004/02/skos/core#"
   "void" "http://rdfs.org/ns/void#"
   "xsd" "http://www.w3.org/2001/XMLSchema#"})


(defn rdf-writer
  "Coerces destination into an java.io.Writer using
  clojure.java.io/writer and returns an RDFWriter.

  Use this to capture the intention to write to a location in a
  specific RDF format, e.g.

  (grafter.rdf/add (rdf-writer \"/tmp/foo.nt\" :format :nt) quads)

  Accepts also the following optional options:

  - :append        If set to true it will append new values to the end of
                   the file destination (default: `false`).

  - :format        If a String or a File are provided the format parameter
                   can be optional (in which case it will be infered from
                   the file extension).  This should be a clojure keyword 
                   representing the format extension e.g. :nt.

  - :encoding      The character encoding to be used (default: UTF-8)

  - :prefixes      A map of RDF prefix names to URI prefixes."

  ([destination & {:keys [append format encoding prefixes] :or {append false
                                                                encoding "UTF-8"
                                                                prefixes default-prefixes}}]

   (let [^RDFFormat format (resolve-format-preference destination format)
         iowriter (fmt/select-output-coercer format)
         writer (Rio/createWriter format
                                  (iowriter destination
                                            :append append
                                            :encoding encoding))]

     (reduce (fn [writer [name prefix]]
               (doto writer
                 (.handleNamespace name (str prefix)))) writer prefixes))))

(def ^:no-doc format-supports-graphs #{RDFFormat/NQUADS
                                       RDFFormat/TRIX
                                       RDFFormat/TRIG})

(defn- write-namespaces
  "Signal to the writer that we're about to send RDF data.  This will
  also trigger any buffered prefixes to be written to the stream."
  [target]
  (.startRDF target))

(defn- end-rdf
  "Signal to the writer that we've finished sending RDF data."
  [target]
  (.endRDF target))

(extend-protocol pr/ITripleWriteable
  RDFHandler
  (pr/add-statement [this statement]
    (.handleStatement this (->backend-type statement)))

  (pr/add
    ([this triples]
     (cond
       (seq triples)
       (do
         (write-namespaces this)
         (doseq [t triples]
           (pr/add-statement this t))
         (end-rdf this))
       (nil? (seq triples)) (do (.startRDF this)
                                (.endRDF this))
       :else (throw (IllegalArgumentException. "This serializer was given an unknown type it must be passed a sequence of Statements."))))

    ([this graph triples]
     (if (format-supports-graphs (.getRDFFormat this))
       (pr/add this (map (fn [s] (assoc s :c graph)) triples))
       (pr/add this triples)))))

;; http://clj-me.cgrand.net/2010/04/02/pipe-dreams-are-not-necessarily-made-of-promises/
(defn- pipe
  "Returns a pair: a seq (the read end) and a function (the write end).
  The function can takes either no arguments to close the pipe
  or one argument which is appended to the seq. Read is blocking."
  [^Integer size]
  (let [q (java.util.concurrent.LinkedBlockingQueue. size)
        EOQ (Object.)
        NIL (Object.)
        pull (fn pull [] (lazy-seq (let [x (.take q)]
                                    (when-not (= EOQ x)
                                      (cons (when-not (= NIL x) x) (pull))))))]
    [(pull) (fn put! ([] (.put q EOQ)) ([x] (.put q (or x NIL))))]))

  ;; WARNING: This implementation is necessarily a little convoluted
  ;; as we hack around Sesame to generate a lazy sequence of results.
  ;; Sesame's parse methods always assume you want to consume the
  ;; whole file of triples, so we spawn a thread to consume through
  ;; the file and use a blocking queue of buffer-size elements to pass elements
  ;; back into a lazy sequence on the calling thread.
  ;;
  ;; NOTE also none of these functions really allow for proper
  ;; resource clean-up unless the whole sequence is consumed.
  ;;
  ;; So, the good news is that this means you should be able to read
  ;; and stream huge files.  The bad news is that might leak a file
  ;; handle, unless you consume the whole sequence.
  ;;
  ;; TODO: consider how to support proper resource cleanup.
(defn- to-statements* [is-or-rdr {:keys [format buffer-size base-uri] :or {buffer-size 32
                                                                           base-uri "http://example.org/base-uri"} :as options}]
  (let [coercer (fmt/select-input-coercer format)
        reader (coercer is-or-rdr :buffer-size buffer-size)]
    (if-not format
      (throw (ex-info (str "The RDF format was neither specified nor inferable from this object.") {:error :no-format-supplied}))
      (let [[statements put!] (pipe buffer-size)
            parser (doto (fmt/format->parser (fmt/->rdf-format format))
                     (.setRDFHandler (reify RDFHandler
                                       (startRDF [this])
                                       (endRDF [this]
                                         (put!)
                                         (.close reader))
                                       (handleStatement [this statement]
                                         (put! statement))
                                       (handleComment [this comment])
                                       (handleNamespace [this prefix-str uri-str]))))]
        (future
          (try
            (.parse parser reader (str base-uri))
            (catch Exception ex
              (put! ex))))
        (let [read-rdf (fn read-rdf [msg]
                         (if (instance? Throwable msg)
                           ;; if the other thread puts an Exception on
                           ;; the pipe, raise it here.
                           (throw (ex-info "Reading triples aborted."
                                           {:error :reading-aborted} msg))
                           (backend-quad->grafter-quad msg)))]
          (map read-rdf statements))))))

(extend-protocol pr/ITripleReadable
  clojure.lang.Sequential
  (pr/to-statements [this options]
    ;; Assume it contains Quads and pass it through, if it doesn't it
    ;; will fail later anyway
    this)

  String
  (pr/to-statements [this options]
    (try
      (pr/to-statements (java.net.URL. this) options)
      (catch MalformedURLException ex
        (pr/to-statements (File. this) options))))

  URL
  (pr/to-statements [this options]
    (pr/to-statements (java.net.URI. (str this)) options))

  URI
  (pr/to-statements [this options]
    (pr/to-statements (java.net.URI (str this)) options))

  java.net.URI
  (pr/to-statements [this options]
    (let [s (str this)]
      (cond
        (string/starts-with? s "file://")
        (pr/to-statements (java.io.File. (string/replace s "file://" "")) options)

        (string/starts-with? s "file:") ;; Resource URIs have this format
        (pr/to-statements (java.io.File. (string/replace s "file:" "")) options)

        :else
        (pr/to-statements (io/reader this) options))))

  File
  (pr/to-statements [this {:keys [format] :as opts}]
    (let [format (resolve-format-preference this format)]
      (to-statements* this (assoc opts :format format))))

  java.io.InputStream
  (pr/to-statements [this opts]
    (to-statements* this opts))

  java.io.Reader
  (pr/to-statements [this opts]
    (to-statements* this opts)))

(extend-protocol IURIable
  org.eclipse.rdf4j.model.URI
  (->java-uri [t]
    (java.net.URI. (str t))))

(extend-protocol ToGrafterURL
  URI
  (->grafter-url [uri]
    (-> uri
        str
        ->grafter-url)))

(defprotocol ToRDF4JURI
  (->rdf4j-uri [this] "Coerce an object into a sesame URIImpl"))

(extend-protocol ToRDF4JURI
  String
  (->rdf4j-uri [this] (URIImpl. this))
  URL
  (->rdf4j-uri [this] (URIImpl. (str this)))
  java.net.URI
  (->rdf4j-uri [this] (URIImpl. (str this)))
  org.eclipse.rdf4j.model.URI
  (->-rdf4j-uri [this] this)
  GrafterURL
  (->-rdf4j-uri [this] (URIImpl. (str this)))
  org.eclipse.rdf4j.model.Graph
  (->-rdf4j-uri [this] (URIImpl. (str this))))
