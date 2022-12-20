(ns ^{:added "0.12.1"}
 grafter-2.rdf4j.io
  "Functions & Protocols for serializing Grafter Statements to (and from)
  any Linked Data format supported by RDF4j."
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [grafter-2.rdf.protocols :as pr :refer [->grafter-type ->Quad IGrafterRDFType]]
            [grafter-2.rdf4j.formats :as fmt]
            [grafter.url :refer [->grafter-url ->java-uri ->url IURIable ToGrafterURL]])
  (:import grafter.url.GrafterURL
           [grafter_2.rdf.protocols IStatement LangString OffsetDate Quad RDFLiteral]
           java.io.File
           [java.net MalformedURLException URL]
           [java.time LocalTime]
           [java.time.temporal ChronoField Temporal TemporalField]
           [javax.xml.datatype DatatypeConstants DatatypeFactory XMLGregorianCalendar]
           [org.eclipse.rdf4j.model BNode Literal Statement IRI Value Model]
           [org.eclipse.rdf4j.model.impl SimpleBNode GenericStatement SimpleLiteral SimpleValueFactory SimpleIRI]
           [org.eclipse.rdf4j.rio RDFFormat RDFHandler Rio]))

(extend-type Statement
  ;; Extend our IStatement protocol to Sesame's Statements for convenience.
  pr/IStatement
  (subject [this] (.getSubject this))
  (predicate [this] (.getPredicate this))
  (object [this] (.getObject this))
  (context [this] (.getContext this)))

(extend-type Literal
  pr/IRDFString
  (lang [this]
    (keyword (.orElse (.getLanguage this) nil)))

  pr/IRawValue
  (raw-value [this]
    (.stringValue this))

  pr/IDatatypeURI
  (datatype-uri [this]
    (URI. (str (.getDatatype this)))))

(defmethod pr/blank-node? BNode [_]
  true)

(defprotocol IRDF4jConverter
  (->backend-type ^Value [this] "Convert an arbitrary statement type into an RDF4j Statement type"))

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
  (let [raw (pr/raw-value literal)]
    (case raw
      "INF" Double/POSITIVE_INFINITY
      "+INF" Double/POSITIVE_INFINITY
      "-INF" Double/NEGATIVE_INFINITY
      (Double/parseDouble raw))))

(defmethod backend-literal->grafter-type "http://www.w3.org/2001/XMLSchema#float" [literal]
  (let [raw (pr/raw-value literal)]
    (case raw
      "INF" Float/POSITIVE_INFINITY
      "+INF" Float/POSITIVE_INFINITY
      "-INF" Float/NEGATIVE_INFINITY
      (Float/parseFloat raw))))

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

(defn- has-second? [^XMLGregorianCalendar xml-cal]
  (let [tz (.getSecond xml-cal)]
    (not= tz DatatypeConstants/FIELD_UNDEFINED)))

(defn- has-time? [^XMLGregorianCalendar xml-cal]
  (let [time (.getHour xml-cal)]
    (= time DatatypeConstants/FIELD_UNDEFINED)))

(defn- has-timezone? [^XMLGregorianCalendar xml-cal]
  (let [tz (.getTimezone xml-cal)]
    (not= tz DatatypeConstants/FIELD_UNDEFINED)))

(defn- xml-cal->local-time
  "NOTE: this function returns either a LocalTime or an OffsetTime.
  TODO: consider refactoring so we can avoid the reflective call."
  [^XMLGregorianCalendar xml-cal]
  (let [hour (let [h (.getHour xml-cal)]
               (if (= 24 h)
                 0
                 h))
        min (.getMinute xml-cal)
        sec (.getSecond xml-cal)
        frac (.getFractionalSecond xml-cal)

        local-time (if (has-second? xml-cal)
                     (if frac
                       (let [nanos (.multiply frac
                                              1000000000M
                                              (java.math.MathContext. 9 java.math.RoundingMode/DOWN))
                             ;; Max allowed value for LocalTime nanos
                             ;; is 999999999, so if we have a fraction
                             ;; of 0.999999999999999 we want to round
                             ;; down so as not to overflow.
                             ]
                         (java.time.LocalTime/of hour min sec nanos))
                       (java.time.LocalTime/of hour min sec))
                     (java.time.LocalTime/of hour min))]

    (if (has-timezone? xml-cal)
      (let [tz-seconds (* 60 (.getTimezone xml-cal))]
        (java.time.OffsetTime/of local-time (java.time.ZoneOffset/ofTotalSeconds tz-seconds)))
      local-time)))

(defmethod backend-literal->grafter-type "http://www.w3.org/2001/XMLSchema#time" [^Literal literal]
  (xml-cal->local-time (.calendarValue literal)))

(defn- xml-cal->local-date [^XMLGregorianCalendar xml-cal]
  (let [year (.getYear xml-cal)
        month (.getMonth xml-cal)
        day (.getDay xml-cal)
        local-time (java.time.LocalDate/of year month day)]

    (if (has-timezone? xml-cal)
      (let [tz-seconds (* 60 (.getTimezone xml-cal))]
        (pr/->OffsetDate local-time (java.time.ZoneOffset/ofTotalSeconds tz-seconds)))
      local-time)))

(defmethod backend-literal->grafter-type "http://www.w3.org/2001/XMLSchema#dateTime" [^Literal literal]
  (let [xml-cal (.calendarValue literal)
        date (xml-cal->local-date xml-cal)
        time ^LocalTime (xml-cal->local-time xml-cal)]
    (if (has-timezone? xml-cal)
      (java.time.OffsetDateTime/of (:date date) (.toLocalTime time) (.getOffset time))
      (java.time.LocalDateTime/of date time))))

(defmethod backend-literal->grafter-type "http://www.w3.org/2001/XMLSchema#date" [^Literal literal]
  (xml-cal->local-date (.calendarValue literal)))

(defmethod backend-literal->grafter-type :default [literal]
  ;; If we don't have a type conversion for it, let the RDF4j type
  ;; through, as it's not really up to grafter to fail the processing,
  ;; as they might just want to pass data through rather than
  ;; understand it.
  (pr/->RDFLiteral (pr/raw-value literal) (pr/datatype-uri literal)))

(def ^:private value-factory (SimpleValueFactory/getInstance))

(defn quad->backend-quad
  "Convert a grafter IStatement into a backend (RDF4j) statement type."
  [^IStatement is]
  (try
    (if (pr/context is)
      (.createStatement value-factory
                        (->backend-type (pr/subject is))
                        (->backend-type (pr/predicate is))
                        (->backend-type (pr/object is))
                        (->backend-type (pr/context is)))
      (.createStatement value-factory (->backend-type (pr/subject is))
                        (->backend-type (pr/predicate is))
                        (->backend-type (pr/object is))))
    (catch ClassCastException cce
      ;; We could really make do with just letting the ClassCastException raise,
      ;; but improve the message a little to nudge developers in the right
      ;; direction, about what is likely to be wrong.
      (throw (ex-info "Error outputing Quad.  It looks like you have an incorrect data type inside a quad.  Check your IRI's are not strings."
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
    (.. (SimpleValueFactory/getInstance) (createLiteral ^String (:string this) (name (:lang this)))))

  String
  (->backend-type [this]
    (.. (SimpleValueFactory/getInstance) (createLiteral this)))

  java.net.URL
  (->backend-type [this]
    (.createIRI value-factory (str this)))

  java.net.URI
  (->backend-type [this]
    (.createIRI value-factory (str this)))

  java.util.Date
  (->backend-type [this]
    (.. (SimpleValueFactory/getInstance) (createLiteral this)))

  Quad
  (->backend-type [this]
    (quad->backend-quad this))

  IRI
  (->backend-type [this]
    this)

  Literal
  (->backend-type [this]
    this)

  Statement
  (->backend-type [this]
    this)

  grafter_2.rdf.protocols.BNode
  (->backend-type [this]
    (.createBNode value-factory (.id this)))

  RDFLiteral
  (->backend-type [this]
    (.createLiteral value-factory (pr/raw-value this) (.createIRI value-factory (str (pr/datatype-uri this))))))

;; Dates and times

(defn- get-temporal-field [^Temporal temporal-obj ^TemporalField temporal-field]
  (if (.isSupported temporal-obj temporal-field)
    (.get temporal-obj temporal-field)
    DatatypeConstants/FIELD_UNDEFINED))

(defn- temporal-object->xml-cal ^XMLGregorianCalendar [^Temporal temporal-obj ^java.time.ZoneOffset offset-obj]
  (let [factory (DatatypeFactory/newInstance)]
    (.newXMLGregorianCalendar ^javax.xml.datatype.DatatypeFactory factory
                              (when (.isSupported temporal-obj ChronoField/YEAR)
                                (biginteger (get-temporal-field temporal-obj ChronoField/YEAR)))
                              (int (get-temporal-field temporal-obj ChronoField/MONTH_OF_YEAR))
                              (int (get-temporal-field temporal-obj ChronoField/DAY_OF_MONTH))
                              (int (get-temporal-field temporal-obj ChronoField/HOUR_OF_DAY))
                              (int (get-temporal-field temporal-obj ChronoField/MINUTE_OF_HOUR))
                              (int (get-temporal-field temporal-obj ChronoField/SECOND_OF_MINUTE))

                              (when (.isSupported temporal-obj ChronoField/NANO_OF_SECOND)
                                (let [nano (.get temporal-obj ChronoField/NANO_OF_SECOND)]
                                  (if (zero? nano)
                                    (bigdec nano)
                                    (-> nano

                                        bigdec
                                        (.divide 1000000000.0M)))))

                              (int (if offset-obj
                                     (let [tz-offset (Math/round (/ (.getTotalSeconds offset-obj) 60.0))]
                                       tz-offset)
                                     DatatypeConstants/FIELD_UNDEFINED)))))

(defn- build-temporal-literal [this tz]
  (.. (SimpleValueFactory/getInstance) (createLiteral (temporal-object->xml-cal this tz))))

(extend-protocol IRDF4jConverter

  ;; java.util.Date
  ;; (->grafter-type [this]
  ;;   this)

  java.time.OffsetDateTime
  (->backend-type [this]
    (build-temporal-literal this (.getOffset this)))

  ;; java.time.ZonedDateTime
  ;; (->backend-type [this]
  ;;   this)

  java.time.LocalDate
  (->backend-type [this]
    (build-temporal-literal this nil))

  java.time.OffsetTime
  (->backend-type [this]
    (build-temporal-literal this (.getOffset this)))

  java.time.LocalTime
  (->backend-type [this]
    (build-temporal-literal this nil))

  java.time.LocalDateTime
  (->backend-type [this]
    (build-temporal-literal this nil))

  OffsetDate
  (->backend-type [this]
    (build-temporal-literal (.date this) (.timezone this))))


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

  IRI
  (->grafter-type [this]
    (->java-uri this))

  java.net.URI
  (->grafter-type [this]
    this)

  BNode
  (->grafter-type [this]
    (-> this .getID pr/make-blank-node))

  grafter_2.rdf.protocols.BNode
  (->grafter-type [this]
    this)

  String
  ;; Assume URI's are the norm not strings
  (->grafter-type [this]
    this)

  LangString
  (->grafter-type [t]
    t))


;; Dates and times

(extend-protocol IGrafterRDFType

  ;; java.util.Date
  ;; (->grafter-type [this]
  ;;   this)

  java.time.OffsetDateTime
  (->grafter-type [this]
    this)

  java.time.ZonedDateTime
  (->grafter-type [this]
    this)

  java.time.LocalDate
  (->grafter-type [this]
    this)

  java.time.LocalDateTime
  (->grafter-type [this]
    this)

  java.time.OffsetTime
  (->grafter-type [this]
    this)

  java.time.LocalTime
  (->grafter-type [this]
    this)

  OffsetDate
  (->grafter-type [this]
    this))

(extend-type GrafterURL
  IGrafterRDFType
  (->grafter-type [uri]
    (->url (str uri)))

  IRDF4jConverter
  (->backend-type [uri]
    (.createIRI value-factory (str uri))))



(defn backend-quad->grafter-quad
  "Convert an RDF4j backend quad into a grafter Quad."
  [^Statement st]
  ;; TODO fix this to work properly with object & context.
  ;; context should return either nil or a IRI
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

  (grafter-2.rdf/add (rdf-writer \"/tmp/foo.nt\" :format :nt) quads)

  Accepts also the following optional options:

  - :append        If set to true it will append new values to the end of
                   the file destination (default: `false`).

  - :format        If a String or a File are provided the format parameter
                   can be optional (in which case it will be infered from
                   the file extension).  This should be a clojure keyword
                   representing the format extension e.g. :nt.

  - :encoding      The character encoding to be used (default: UTF-8)

  - :prefixes      A map of RDF prefix names to IRI prefixes."

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

  IRI
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
    (to-statements* this opts))

  Model
  (to-statements [this _opts]
    ;; Works because a Model is a java.util.Set
    (map backend-quad->grafter-quad this)))

(extend-protocol IURIable
  org.eclipse.rdf4j.model.IRI
  (->java-uri [t]
    (java.net.URI. (str t))))

(extend-protocol ToGrafterURL
  IRI
  (->grafter-url [uri]
    (-> uri
        str
        ->grafter-url)))

(defprotocol ToRDF4JURI
  (->rdf4j-uri [this] "Coerce an object into an RDF4j IRI"))

(extend-protocol ToRDF4JURI
  String
  (->rdf4j-uri [this] (.createIRI value-factory this))
  URL
  (->rdf4j-uri [this] (.createIRI value-factory (str this)))
  java.net.URI
  (->rdf4j-uri [this] (.createIRI value-factory (str this)))
  org.eclipse.rdf4j.model.IRI
  (->-rdf4j-uri [this] this)
  GrafterURL
  (->-rdf4j-uri [this] (.createIRI value-factory (str this))))

(defn statements
  "Attempts to coerce an arbitrary source of RDF statements into a
  sequence of grafter Statements, using the RDF4j backend.

  If the source is a quad store quads from all the named graphs will
  be returned.  Any triples in an unnamed graph will be ignored.

  Takes optional parameters which may be used depending on the
  context e.g. specifiying the format of the source triples.

  The `:format` option is supplied by the wrapping function and may be
  nil, or act as an indicator about the format of the triples to read.
  Implementers can choose whether or not to ignore or require the
  format parameter.

  The `:buffer-size` option can be used to configure the buffer size
  at which statements are parsed from an RDF stream.  Its default
  value of 32 was found to work well in practice, and also aligns with
  chunk size of Clojure's lazy sequences.

  The `:base-uri` option can be supplied to automatically re`@base`
  IRI's on a new prefix when reading."
  [this & {:keys [format buffer-size base-uri] :as options}]
  (pr/to-statements this options))
