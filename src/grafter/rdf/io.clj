(ns grafter.rdf.io
  "Functions & Protocols for serializing Grafter Statements to (and from)
  any Linked Data format supported by Sesame."
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [grafter.rdf
             [formats :as fmt]
             [protocols :as pr :refer [->Quad]]]
            [grafter.url
             :refer
             [->grafter-url ->java-uri ->url IURIable ToGrafterURL]])
  (:import [grafter.rdf.protocols IStatement Quad RDFLiteral]
           grafter.url.GrafterURL
           java.io.File
           [java.net MalformedURLException URL]
           java.util.GregorianCalendar
           javax.xml.datatype.DatatypeFactory
           [org.openrdf.model BNode Literal Resource Statement URI Value]
           [org.openrdf.model.impl BNodeImpl BooleanLiteralImpl CalendarLiteralImpl ContextStatementImpl IntegerLiteralImpl LiteralImpl NumericLiteralImpl StatementImpl URIImpl]
           [org.openrdf.rio RDFFormat RDFHandler RDFWriter Rio]))

(extend-type Statement
  ;; Extend our IStatement protocol to Sesame's Statements for convenience.
  pr/IStatement
  (subject [this] (.getSubject this))
  (predicate [this] (.getPredicate this))
  (object [this] (.getObject this))
  (context [this] (.getContext this)))

(defprotocol ISesameRDFConverter
  (->sesame-rdf-type [this] "Convert a native type into a Sesame RDF Type")
  (sesame-rdf-type->type [this] "Convert a Sesame RDF Type into a Native Type"))

(defn language
  "Create an RDF langauge string out of a value string and a given
  language tag.  Language tags should be keywords representing the
  country code, e.g.

  (language \"Bonsoir\" :fr)"
  [s lang]
  {:pre [(string? s)
         lang
         (keyword? lang)]}
  (pr/->LangString s lang))

(defn literal
  "You can use this to declare an RDF typed literal value along with
  its URI.  Note that there are implicit coercions already defined for
  many core clojure/java datatypes, so for common datatypes you
  shounld't need this."

  [val datatype-uri]
  (pr/->RDFLiteral (str val) (->java-uri datatype-uri)))

(defmulti literal-datatype->type
  "A multimethod to convert an RDF literal into a corresponding
  Clojure type.  This method can be extended to provide custom
  conversions."
  (fn [lit]
    (when-let [datatype (pr/datatype-uri lit)]
      (str datatype))))

(defmethod literal-datatype->type nil [literal]
  (language (pr/raw-value literal) (pr/lang literal)))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#boolean" [literal]
  (Boolean/parseBoolean (pr/raw-value literal)))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#byte" [literal]
  (Byte/parseByte (pr/raw-value literal)))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#short" [literal]
  (Short/parseShort (pr/raw-value literal)))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#decimal" [literal]
  ;; Prefer clj's big integer over java's because of hash code issue:
  ;; http://stackoverflow.com/questions/18021902/use-cases-for-bigint-versus-biginteger-in-clojure
  (bigdec (pr/raw-value literal)))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#double" [literal]
  (Double/parseDouble (pr/raw-value literal)))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#float" [literal]
  (Float/parseFloat (pr/raw-value literal)))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#integer" [literal]
  (bigint (pr/raw-value literal)))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#int" [literal]
  (java.lang.Integer/parseInt (pr/raw-value literal)))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#long" [literal]
  (java.lang.Long/parseLong (pr/raw-value literal)))

(defmethod literal-datatype->type "http://www.w3.org/TR/xmlschema11-2/#string" [literal]
  (language (pr/raw-value literal) (pr/lang literal)))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#string" [literal]
  (pr/raw-value literal))

(defmethod literal-datatype->type "http://www.w3.org/1999/02/22-rdf-syntax-ns#langString" [literal]
  (language (pr/raw-value literal) (pr/lang literal)))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#dateTime" [literal]
  (-> literal .calendarValue .toGregorianCalendar .getTime))

(defmethod literal-datatype->type :default [literal]
  ;; If we don't have a type conversion for it, let the sesame type
  ;; through, as it's not really up to grafter to fail the processing,
  ;; as they might just want to pass data through rather than
  ;; understand it.
  literal)

(extend-protocol ISesameRDFConverter
  ;; Numeric Types

  RDFLiteral
  (->sesame-rdf-type [this]
    (LiteralImpl. (pr/raw-value this) (URIImpl. (str (pr/datatype-uri this)))))

  (sesame-rdf-type->type [this]
    this)

  java.lang.Byte
  (->sesame-rdf-type [this]
    (NumericLiteralImpl. this (URIImpl. "http://www.w3.org/2001/XMLSchema#byte")))

  (sesame-rdf-type->type [this]
    this)

  java.lang.Short
  (->sesame-rdf-type [this]
    (NumericLiteralImpl. this (URIImpl. "http://www.w3.org/2001/XMLSchema#short")))

  (sesame-rdf-type->type [this]
    this)

  java.math.BigDecimal
  (->sesame-rdf-type [this]
    (NumericLiteralImpl. this (URIImpl. "http://www.w3.org/2001/XMLSchema#decimal")))

  (sesame-rdf-type->type [this]
    this)

  java.lang.Double
  (->sesame-rdf-type [this]
    (NumericLiteralImpl. this))

  (sesame-rdf-type->type [this]
    this)

  java.lang.Float
  (->sesame-rdf-type [this]
    (NumericLiteralImpl. this))

  (sesame-rdf-type->type [this]
    this)

  java.lang.Integer
  (->sesame-rdf-type [this]
    (NumericLiteralImpl. this))

  (sesame-rdf-type->type [this]
    this)

  java.math.BigInteger
  (->sesame-rdf-type [this]
    (NumericLiteralImpl. this (URIImpl. "http://www.w3.org/2001/XMLSchema#integer")))

  (sesame-rdf-type->type [this]
    this)

  java.lang.Long
  (->sesame-rdf-type [this]
    (NumericLiteralImpl. (long this)))

  (sesame-rdf-type->type [this]
    this)

  clojure.lang.BigInt
  (->sesame-rdf-type [this]
    (IntegerLiteralImpl. (BigInteger. (str this))))

  (sesame-rdf-type->type [this]
    this))

(defn IStatement->sesame-statement
  "Convert a grafter IStatement into a Sesame statement."
  [^IStatement is]
  (try
    (if (pr/context is)
      (ContextStatementImpl. (->sesame-rdf-type (pr/subject is))
                             (->sesame-rdf-type (pr/predicate is))
                             (->sesame-rdf-type (pr/object is))
                             (->sesame-rdf-type (pr/context is)))
      (StatementImpl. (->sesame-rdf-type (pr/subject is))
                      (->sesame-rdf-type (pr/predicate is))
                      (->sesame-rdf-type (pr/object is))))
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

(extend-protocol ISesameRDFConverter

  java.lang.Boolean
  (->sesame-rdf-type [this]
    (BooleanLiteralImpl. this))

  (sesame-rdf-type->type [this]
    this)

  BooleanLiteralImpl
  (->sesame-rdf-type [this]
    this)

  Statement
  (->sesame-rdf-type [this]
    this)

  (sesame-rdf-type->type [this]
    this)

  Quad
  (->sesame-rdf-type [this]
    (IStatement->sesame-statement this))

  Value
  (->sesame-rdf-type [this]
    this)

  Resource
  (->sesame-rdf-type [this]
    this)

  Literal
  (->sesame-rdf-type [this]
    this)

  (sesame-rdf-type->type [this]
    (literal-datatype->type this))

  URI
  (->sesame-rdf-type [this]
    this)

  (sesame-rdf-type->type [this]
    (->java-uri this))

  java.net.URI
  (->sesame-rdf-type [this]
    (URIImpl. (.toString this)))

  (sesame-rdf-type->type [this]
    this)

  java.net.URL
  (->sesame-rdf-type [this]
    (URIImpl. (.toString this)))

  BNode
  (->sesame-rdf-type [this]
    this)

  (sesame-rdf-type->type [this]
    (-> this .getID keyword))

  BNodeImpl
  (->sesame-rdf-type [this]
    this)

  (sesame-rdf-type->type [this]
    (-> this .getID keyword))

  java.util.Date
  (->sesame-rdf-type [this]
    this)

  (->sesame-rdf-type [this]
    (let [cal (doto (GregorianCalendar.)
                (.setTime this))]
      (-> (DatatypeFactory/newInstance)
          (.newXMLGregorianCalendar cal)
          CalendarLiteralImpl.)))

  clojure.lang.Keyword
  (->sesame-rdf-type [this]
    (BNodeImpl. (name this)))

  String
  ;; Assume URI's are the norm not strings
  (->sesame-rdf-type [this]
    (LiteralImpl. this))

  (sesame-rdf-type->type [this]
    this)

  grafter.rdf.protocols.LangString
  (->sesame-rdf-type [t]
    (LiteralImpl. (pr/raw-value t) (name (pr/lang t))))

  (sesame-rdf-type->type [t]
    t))

(extend-protocol ISesameRDFConverter
  GrafterURL

  (sesame-rdf-type->type [uri]
    (->url (str uri)))

  (->sesame-rdf-type [uri]
    (URIImpl. (str uri))))

;; Extend IURIable protocol to sesame URI's.
(extend-protocol IURIable

  org.openrdf.model.URI

  (->java-uri [this]
    (java.net.URI. (.stringValue this))))

(defn sesame-statement->IStatement
  "Convert a sesame Statement into a grafter Quad."
  [^Statement st]
  ;; TODO fix this to work properly with object & context.
  ;; context should return either nil or a URI
  ;; object should be converted to a clojure type.
  (->Quad (sesame-rdf-type->type (.getSubject st))
          (->java-uri (.getPredicate st))
          (sesame-rdf-type->type (.getObject st))
          (when-let [graph (.getContext st)]
            (sesame-rdf-type->type graph))))

(defn ^{:deprecated "0.8.0"} filename->rdf-format
  "DEPRECATED: Use grafter.rdf.formats/filename->rdf-format instead.

  Given a filename we attempt to return an appropriate RDFFormat
  object based on the files extension."
  [fname]
  (if (nil? fname)
    (fmt/filename->rdf-format fname)))

(defn ^{:deprecated "0.8.0"} mimetype->rdf-format
  "DEPRECATED: Use grafter.rdf.formats/mimetype->rdf-format instead.

  Given a mimetype string we attempt to return an appropriate
  RDFFormat object based on the files extension."
  [mime-type]
  (if (nil? mime-type)
    (throw (IllegalArgumentException. "Mime type required"))
    (fmt/mimetype->rdf-format mime-type)))

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

(def default-prefixes
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


(defn rdf-serializer
  "Coerces destination into an java.io.Writer using
  clojure.java.io/writer and returns an RDFSerializer.

  Use this to capture the intention to write to a location in a
  specific RDF format, e.g.

  (grafter.rdf/add (rdf-serializer \"/tmp/foo.nt\" :format :nt) quads)

  Accepts also the following optional options:

  - :append        If set to true it will append new values to the end of
                   the file destination (default: `false`).

  - :format        If a String or a File are provided the format parameter
                   can be optional (in which case it will be infered from
                   the file extension).  This should be a sesame RDFFormat
                   object.

  - :encoding      The character encoding to be used (default: UTF-8)

  - :prefixes      A map of RDF prefix names to URI prefixes."

  ([destination & {:keys [append format encoding prefixes] :or {append false
                                                                encoding "UTF-8"
                                                                prefixes default-prefixes}}]

   (let [^RDFFormat format (resolve-format-preference destination format)
         writer (Rio/createWriter format
                                  (io/writer destination
                                             :append append
                                             :encoding encoding))]


     (reduce (fn [acc [name prefix]]
               (doto writer
                 (.handleNamespace name prefix))) writer prefixes))))

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
  RDFWriter
  (pr/add-statement [this statement]
    (.handleStatement this (->sesame-rdf-type statement)))

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
      (pr/to-statements (io/reader this) (assoc opts :format format))))

  java.io.InputStream
  (pr/to-statements [this opts]
    (pr/to-statements (io/reader this) opts))

  java.io.Reader
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
  (pr/to-statements [reader {:keys [format buffer-size base-uri] :or {buffer-size 32
                                                                      base-uri "http://example.org/base-uri"} :as options}]
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
                           (sesame-statement->IStatement msg)))]
          (map read-rdf statements))))))

(extend-protocol ToGrafterURL
  URI
  (->grafter-url [uri]
    (-> uri
        str
        ->grafter-url)))

(defprotocol ToSesameURI
  (->sesame-uri [this] "Coerce an object into a sesame URIImpl"))

(extend-protocol ToSesameURI
  String
  (->sesame-uri [this] (URIImpl. this))
  URL
  (->sesame-uri [this] (URIImpl. (str this)))
  java.net.URI
  (->sesame-uri [this] (URIImpl. (str this)))
  org.openrdf.model.URI
  (->sesame-uri [this] this)
  GrafterURL
  (->sesame-uri [this] (URIImpl. (str this)))
  org.openrdf.model.Graph
  (->sesame-uri [this] (URIImpl. (str this))))
