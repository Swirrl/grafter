(ns grafter.rdf4j.formats
  "Symbols used to specify different Linked Data Serializations.

  Includes functions to coerce formats from clojure keywords / file
  extension strings into their underlying RDF4j RDFFormat object.  

  Supported format keywords are:

  :brf 
  :json
  :n3
  :nq
  :nt
  :rdf (also :owl :rdfs :xml) 
  :rj
  :trig
  :trix
  :ttl"
  (:require [clojure.string :as string]
            [grafter.url :as url]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import [org.eclipse.rdf4j.rio RDFFormat RDFParser RDFParserFactory Rio]
           org.eclipse.rdf4j.rio.binary.BinaryRDFParserFactory
           org.eclipse.rdf4j.rio.jsonld.JSONLDParserFactory
           org.eclipse.rdf4j.rio.n3.N3ParserFactory
           org.eclipse.rdf4j.rio.nquads.NQuadsParserFactory
           org.eclipse.rdf4j.rio.ntriples.NTriplesParserFactory
           org.eclipse.rdf4j.rio.rdfjson.RDFJSONParserFactory
           org.eclipse.rdf4j.rio.rdfxml.RDFXMLParserFactory
           org.eclipse.rdf4j.rio.trig.TriGParserFactory
           org.eclipse.rdf4j.rio.trix.TriXParserFactory
           org.eclipse.rdf4j.rio.turtle.TurtleParserFactory))

(defmulti mimetype->rdf-format
  "Extensible multimethod that accepts a mime-type string and returns
  the appropriate sesame RDFFormat object.

  NOTE: the ->rdf-format function also uses this, and supports both
  mime-types and file extensions." (fn [s]
                                     (if (string? s)
                                       (string/trim (first (string/split s #";")))
                                       s)))

(defmethod mimetype->rdf-format :default [fmt]
  nil)

(def ->rdf-format)

(defmulti ->rdf-format
  "Extensible multi-method that coerces mime-type strings, or strings
  or keys representing file extensions for RDF types into RDFFormat
  objects for use with Sesame.

  e.g. it can coerce :nt, \"nt\" or \"application/n-triples\" into the
  appropriate RDFFormat object.
  "
  (fn [fmt]
    (cond (instance? RDFFormat fmt)
          ::coerced
          (instance? String fmt)
          String

          (keyword? fmt)
          fmt

          :else (type fmt))))

(defmethod ->rdf-format String [fmt]
  (if (re-find #"/" fmt)
    (mimetype->rdf-format fmt)
    (->rdf-format (keyword (str/replace fmt "." "")))))

(defmethod ->rdf-format ::coerced [fmt] fmt)

(defmethod ->rdf-format :default [fmt]
  nil)

(defn filename->rdf-format
  "Given a filename we attempt to return an appropriate RDFFormat
  object based on the files extension."
  [fname]
  (.orElse (Rio/getParserFormatForFileName (str fname)) nil))

(defn url->rdf-format
  "Parse a URL for the file extension of its last path segment,
  ignoring query string and other URL parts."
  [url]
  (filename->rdf-format (last (url/path-segments url))))

(defmethod ->rdf-format java.io.File [f]
  (filename->rdf-format f))

(defmethod ->rdf-format java.net.URI [f]
  (url->rdf-format f))

(defmethod ->rdf-format java.net.URL [f]
  (url->rdf-format f))

(defmethod ->rdf-format org.eclipse.rdf4j.model.URI [f]
  (url->rdf-format f))

(defn select-input-coercer
  "Depending on whether the format is text or binary returns either a
  Reader or an InputStream."
  [fmt]
  (if (= RDFFormat/BINARY (->rdf-format fmt))
    io/input-stream ;; If we're a binary format we need to use an outputstream not a writer
    io/reader))

(defn select-output-coercer
  "Depending on whether the format is text or binary returns either a
  Writer or an OutputStream."
  [fmt]
  (if (= RDFFormat/BINARY (->rdf-format fmt))
    io/output-stream ;; If we're a binary format we need to use an outputstream not a writer
    io/writer))

(defmacro ^:private def-format
  "Define a bunch of format coercions from mime-type and file
  extension to RDFFormat.  This macro inspects the RDFFormat object it
  is given and generates appropriate multi-methods to do the coercion
  for you.

  If you want to define new coercions custom coercions (not baked into
  sesame) you can extend the ->rdf-format multimethod directly."
  [format]
  `(do
     (doseq [ext# (map keyword (.getFileExtensions ~format))]
       (defmethod ->rdf-format ext# [foo#] ~format))

     (doseq [mt# (.getMIMETypes ~format)]
       (defmethod mimetype->rdf-format mt# [foo#] ~format))))

(def-format RDFFormat/BINARY)
(def-format RDFFormat/JSONLD)
(def-format RDFFormat/RDFJSON)
(def-format RDFFormat/N3)
(def-format RDFFormat/NQUADS)
(def-format RDFFormat/NTRIPLES)
(def-format RDFFormat/TURTLE)
(def-format RDFFormat/TRIG)
(def-format RDFFormat/TRIX)
(def-format RDFFormat/RDFXML)

(defmulti ^:private rdf-format->parser identity)

(defmethod rdf-format->parser RDFFormat/BINARY [_]
  BinaryRDFParserFactory)

(defmethod rdf-format->parser RDFFormat/JSONLD [_]
  JSONLDParserFactory)

(defmethod rdf-format->parser RDFFormat/RDFJSON [_]
  RDFJSONParserFactory)

(defmethod rdf-format->parser RDFFormat/N3 [_]
  N3ParserFactory)

(defmethod rdf-format->parser RDFFormat/NQUADS [_]
  NQuadsParserFactory)

(defmethod rdf-format->parser RDFFormat/NTRIPLES [_]
  NTriplesParserFactory)

(defmethod rdf-format->parser RDFFormat/TURTLE [_]
  TurtleParserFactory)

(defmethod rdf-format->parser RDFFormat/TRIG [_]
  TriGParserFactory)

(defmethod rdf-format->parser RDFFormat/TRIX [_]
  TriXParserFactory)

(defmethod rdf-format->parser RDFFormat/RDFXML [_]
  RDFXMLParserFactory)

(defn ^:no-doc format->parser
  "Convert a format into a sesame parser for that format."
  ^RDFParser
  [format]
  (let [^Class parser-class (rdf-format->parser format)]
    (if-not parser-class
      (throw (ex-info (str "Unsupported format: " (pr-str format)) {:error :unsupported-format})))
    (let [^RDFParserFactory factory (.newInstance parser-class)]
      (.getParser factory))))
