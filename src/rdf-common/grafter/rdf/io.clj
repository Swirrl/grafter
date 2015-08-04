(ns grafter.rdf.io
  "Functions & Protocols for serializing Grafter Statements to (and from)
  any Linked Data format supported by Sesame."
  (:require [clojure.java.io :as io]
            [grafter.rdf.protocols :as pr]
            [clojure.tools.logging :as log]
            [me.raynes.fs :as fs]
            [pantomime.media :as mime]
            [grafter.url :refer [->url ->grafter-url IURIable ToGrafterURL]])
  (:import (grafter.rdf.protocols IStatement Quad Triple)
           (grafter.url GrafterURL)
           (java.io File)
           (java.net MalformedURLException URL)
           (java.util GregorianCalendar)
           (javax.xml.datatype DatatypeFactory)
           (org.openrdf.model BNode Literal Resource Statement URI
                              Value)
           (org.openrdf.model.impl BNodeImpl BooleanLiteralImpl
                                   CalendarLiteralImpl
                                   ContextStatementImpl
                                   IntegerLiteralImpl LiteralImpl
                                   NumericLiteralImpl StatementImpl
                                   URIImpl)
           (org.openrdf.repository Repository RepositoryConnection)
           (org.openrdf.rio RDFFormat RDFHandler RDFWriter Rio RDFParserFactory RDFParser)
           (org.openrdf.rio.n3 N3ParserFactory)
           (org.openrdf.rio.nquads NQuadsParserFactory)
           (org.openrdf.rio.ntriples NTriplesParserFactory)
           (org.openrdf.rio.rdfjson RDFJSONParserFactory)
           (org.openrdf.rio.rdfxml RDFXMLParserFactory)
           (org.openrdf.rio.trig TriGParserFactory)
           (org.openrdf.rio.trix TriXParserFactory)
           (org.openrdf.rio.turtle TurtleParserFactory)))

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

(defn s
  "Cast a string to an RDF literal.  The second optional argument can
  either be a keyword corresponding to an RDF language tag
  e.g. :en, :en-gb, or :fr or a string or URI in which case it is
  assumed to be a URI identifying the RDF type of the literal."
  ([str]
     {:pre [(string? str)]}
     (reify Object
       (toString [_] str)
       ISesameRDFConverter
       (->sesame-rdf-type [this]
         (LiteralImpl. str))))
  ([^String str lang-or-uri]
     {:pre [(string? str) (or (string? lang-or-uri) (keyword? lang-or-uri) (nil? lang-or-uri) (instance? URI lang-or-uri))]}
     (reify Object
       (toString [_] str)
       ISesameRDFConverter
       (->sesame-rdf-type [this]
         (if (instance? URI lang-or-uri)
           (let [^URI uri lang-or-uri] (LiteralImpl. str uri))
           (let [^String t (and lang-or-uri (name lang-or-uri))]
             (LiteralImpl. str t)))))))

(defmulti literal-datatype->type
  "A multimethod to convert an RDF literal into a corresponding
  Clojure type.  This method can be extended to provide custom
  conversions."
  (fn [^Literal literal]
    (when-let [datatype (-> literal .getDatatype)]
      (str datatype))))

(defmethod literal-datatype->type nil [^Literal literal]
  (s (.stringValue literal) (.getLanguage literal)))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#byte" [^Literal literal]
  (.byteValue literal))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#short" [^Literal literal]
  (.shortValue literal))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#decimal" [^Literal literal]
  (.decimalValue literal))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#double" [^Literal literal]
  (.doubleValue literal))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#float" [^Literal literal]
  (.floatValue literal))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#integer" [^Literal literal]
  (.integerValue literal))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#int" [^Literal literal]
  (.intValue literal))

(defmethod literal-datatype->type "http://www.w3.org/TR/xmlschema11-2/#string" [^Literal literal]
  (s (.stringValue literal) (.getLanguage literal)))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#dateTime" [^Literal literal]
  (-> literal .calendarValue .toGregorianCalendar .getTime))

(defmethod literal-datatype->type :default [^Literal literal]
  ;; If we don't have a type conversion for it, let the sesame type
  ;; through, as it's not really up to grafter to fail the processing,
  ;; as they might just want to pass data through rather than
  ;; understand it.
  literal)

(extend-protocol ISesameRDFConverter
  ;; Numeric Types

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
    ;; hacky and probably a little slow but works for now
    (IntegerLiteralImpl. (BigInteger. (str this))))

  (sesame-rdf-type->type [this]
    this)

  clojure.lang.BigInt
  (->sesame-rdf-type [this]
    ;; hacky and probably a little slow but works for now
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

  (sesame-rdf-type->type [this]
    (.booleanValue this))

  java.lang.String
  ;; Assume URI's are the norm not strings
  (->sesame-rdf-type [this]
    (URIImpl. this))

  (sesame-rdf-type->type [this]
    this)

  LiteralImpl
  (->sesame-rdf-type [this]
    this)

  (sesame-rdf-type->type [this]
    (literal-datatype->type this))

  Statement
  (->sesame-rdf-type [this]
    this)

  (sesame-rdf-type->type [this]
    this)

  Triple
  (->sesame-rdf-type [this]
    (IStatement->sesame-statement this))

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
    (str this))

  java.net.URI
  (->sesame-rdf-type [this]
    (URIImpl. (.toString this)))

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
    (let [cal (doto (GregorianCalendar.)
                (.setTime this))]
      (-> (DatatypeFactory/newInstance)
          (.newXMLGregorianCalendar cal)
          CalendarLiteralImpl.)))

  clojure.lang.Keyword
  (->sesame-rdf-type [this]
    (BNodeImpl. (name this))))

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
  (Quad. (str (.getSubject st))
         (str (.getPredicate st))
         (sesame-rdf-type->type (.getObject st))
         (when-let [graph (.getContext st)]
           (sesame-rdf-type->type graph))))

(defn filename->rdf-format
  "Given a filename we attempt to return an appropriate RDFFormat
  object based on the files extension."
  [fname]
  (Rio/getParserFormatForFileName fname))

(defn mimetype->rdf-format
  "Given a mimetype string we attempt to return an appropriate
  RDFFormat object based on the files extension."
  [mime-type]
  (if (nil? mime-type)
    (throw (IllegalArgumentException. "Mime type required"))
    (let [base-type (str (mime/base-type mime-type))]
      (condp = base-type
        "application/rdf+xml" RDFFormat/RDFXML
        "application/xml" RDFFormat/RDFXML
        "text/plain" RDFFormat/NTRIPLES
        "application/n-triples" RDFFormat/NTRIPLES
        "text/turtle" RDFFormat/TURTLE
        "application/x-turtle" RDFFormat/TURTLE
        "text/n3" RDFFormat/N3
        "text/rdf+n3" RDFFormat/N3
        "application/trix" RDFFormat/TRIX
        "application/x-trig" RDFFormat/TRIG
        "application/x-binary-rdf" RDFFormat/BINARY
        "text/x-nquads" RDFFormat/NQUADS
        "application/ld+json" RDFFormat/JSONLD
        "application/rdf+json" RDFFormat/RDFJSON
        "application/xhtml+xml" RDFFormat/RDFA
        "application/html" RDFFormat/RDFA
        (Rio/getParserFormatForMIMEType mime-type)))))

(defn- resolve-format-preference
  "Takes an clojure.java.io destination (e.g. URL/File etc...) and a
  format-preference and tries to resolve them in a fallback chain.

  If format-preference does not resolve then we fallback to the destination's
  file extension if there is one. If no format can be resolved we raise an
  exception.

  format-preference can be a keyword e.g. :ttl, a string of an extension e.g
  \"nt\" or a mime-type.
  "
  [dest format-preference]
  (if (instance? RDFFormat format-preference)
    format-preference
    (or (try (mimetype->rdf-format format-preference) (catch Exception nx nil))
        (filename->rdf-format (str "." format-preference))
        (condp = (class dest)
          String (filename->rdf-format dest)
          File   (filename->rdf-format (str dest)))
        (throw (ex-info "Could not infer file format, please supply a :format parameter" {:error :could-not-infer-file-format :object dest})))))

(defn rdf-serializer
  "Coerces destination into an java.io.Writer using
  clojure.java.io/writer and returns an RDFSerializer.

  Accepts also the following optional options:

  - :append        If set to true it will append new values to the end of
                   the file destination (default: `false`).

  - :format        If a String or a File are provided the format parameter
                   can be optional (in which case it will be infered from
                   the file extension).  This should be a sesame RDFFormat
                   object.

  - :encoding      The character encoding to be used (default: UTF-8)"

  ([destination & {:keys [append format encoding] :or {append false
                                                       encoding "UTF-8"}}]
   (let [^RDFFormat format (resolve-format-preference destination format)]
     (Rio/createWriter format
                       (io/writer destination
                                  :append append
                                  :encoding encoding)))))

(def ^:no-doc format-supports-graphs #{RDFFormat/NQUADS
                                       RDFFormat/TRIX
                                       RDFFormat/TRIG})

(extend-protocol pr/ITripleWriteable
  RDFWriter
  (pr/add-statement [this statement]
    (.handleStatement this (->sesame-rdf-type statement)))

  (pr/add
    ([this triples]
       (if (seq triples)
         (do
           (.startRDF this)
           (doseq [t triples]
             (pr/add-statement this t))
           (.endRDF this))
         (throw (IllegalArgumentException. "This serializer does not support writing a single statement.  It should be passed a sequence of statements."))))

    ([this graph triples]
     (if (format-supports-graphs (.getRDFFormat this))
       (pr/add this (map (fn [s] (assoc s :c graph)) triples))
       (pr/add this triples)))))

(defn ^:no-doc format->parser
  "Convert a format into a sesame parser for that format."
  ^RDFParser
  [format]
  (let [table {RDFFormat/NTRIPLES NTriplesParserFactory
               RDFFormat/TRIX TriXParserFactory
               RDFFormat/TRIG TriGParserFactory
               RDFFormat/RDFXML RDFXMLParserFactory
               RDFFormat/NQUADS NQuadsParserFactory
               RDFFormat/TURTLE TurtleParserFactory
               RDFFormat/JSONLD RDFJSONParserFactory
               RDFFormat/N3 N3ParserFactory
               }
        ^Class parser-class (table format)]
    (if-not parser-class
      (throw (ex-info (str "Unsupported format: " (pr-str format)) {:type :unsupported-format})))
    (let [^RDFParserFactory factory (.newInstance parser-class)]
      (.getParser factory))))

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

  String
  (pr/to-statements [this options]
    (try
      (pr/to-statements (URL. this) options)
      (catch MalformedURLException ex
        (pr/to-statements (File. this) options))))

  URL
  (pr/to-statements [this options]
    (pr/to-statements (io/reader this) options))

  URI
  (pr/to-statements [this options]
    (pr/to-statements (str this) options))

  File
  (pr/to-statements [this opts]
    (let [implied-format (Rio/getParserFormatForFileName (str this))
          options (if implied-format
                    (merge {:format implied-format} opts)
                    opts)]
      (pr/to-statements (io/reader this) options)))

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
  (pr/to-statements [reader {:keys [format buffer-size] :or {buffer-size 32} :as options}]
    (if-not format
      (throw (ex-info (str "The RDF format was neither specified nor inferable from this object.") {:type :no-format-supplied}))
      (let [[statements put!] (pipe buffer-size)]
        (future
          (let [parser (doto (format->parser format)
                         (.setRDFHandler (reify RDFHandler
                                           (startRDF [this])
                                           (endRDF [this]
                                             (put!)
                                             (.close reader))
                                           (handleStatement [this statement]
                                             (put! statement))
                                           (handleComment [this comment])
                                           (handleNamespace [this prefix-str uri-str]))))]
            (try
              (.parse parser reader "http://example.org/base-uri")
              (catch Exception ex
                (put! ex)))))
        (let [read-rdf (fn read-rdf [msg]
                         (if (instance? Exception msg)
                           ;; if the other thread puts an Exception on
                           ;; the pipe, raise it here.
                           (throw (ex-info "Reading triples aborted."
                                           {:type :reading-aborted} msg))
                           (sesame-statement->IStatement msg)))]
          (map read-rdf statements))))))

(extend-protocol ToGrafterURL

  URI
  (->grafter-url [uri]
    (-> uri
        str
        ->grafter-url)))
