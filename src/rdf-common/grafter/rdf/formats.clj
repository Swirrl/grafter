(ns grafter.rdf.formats
  "Symbols used to specify different Linked Data Serializations."
  (:require [clojure.string :as string]
            [grafter.url :as url]
            [clojure.string :as str])
  (:import [org.openrdf.rio RDFFormat RDFParser RDFParserFactory Rio]
           org.openrdf.rio.binary.BinaryRDFParserFactory
           org.openrdf.rio.jsonld.JSONLDParserFactory
           org.openrdf.rio.n3.N3ParserFactory
           org.openrdf.rio.nquads.NQuadsParserFactory
           org.openrdf.rio.ntriples.NTriplesParserFactory
           org.openrdf.rio.rdfjson.RDFJSONParserFactory
           org.openrdf.rio.rdfxml.RDFXMLParserFactory
           org.openrdf.rio.trig.TriGParserFactory
           org.openrdf.rio.trix.TriXParserFactory
           org.openrdf.rio.turtle.TurtleParserFactory))

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
  (Rio/getParserFormatForFileName (str fname)))

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

(defmethod ->rdf-format org.openrdf.model.URI [f]
  (url->rdf-format f))

(defmacro def-format
  "Define a bunch of format coercions from mime-type and file
  extension to RDFFormat.  This macro inspects the RDFFormat object it
  is given and generates appropriate multi-methods to do the coercion
  for you.

  If you want to define new coercions custom coercions (not baked into
  sesame) you can extend the ->rdf-format multimethod directly."
  [sym docstr format]
  `(do
     (def ~sym ~docstr ~format)

     (doseq [ext# (map keyword (.getFileExtensions ~format))]
       (defmethod ->rdf-format ext# [foo#] ~format))

     (doseq [mt# (.getMIMETypes ~format)]
       (defmethod mimetype->rdf-format mt# [foo#] ~format))))

(def-format ^{:deprecated "0.8.0" } rdf-binary "Deprecated. 0.8.0: Sesame's Binary RDFFormat" RDFFormat/BINARY)
(def-format ^{:deprecated "0.8.0" } rdf-jsonld "Deprecated. JSONLD RDF Format" RDFFormat/JSONLD)
(def-format ^{:deprecated "0.8.0" } rdf-json "Deprecated. JSON RDF Format" RDFFormat/RDFJSON)
(def-format ^{:deprecated "0.8.0" } rdf-n3 "Deprecated. N3 RDF Format" RDFFormat/N3)
(def-format ^{:deprecated "0.8.0" } rdf-nquads "Deprecated. NQuads RDFFormat" RDFFormat/NQUADS)
(def-format ^{:deprecated "0.8.0" } rdf-ntriples "Deprecated. NTriples RDFFormat" RDFFormat/NTRIPLES)
(def-format ^{:deprecated "0.8.0" } rdf-turtle "Deprecated. Turtle RDFFormat" RDFFormat/TURTLE)
(def-format ^{:deprecated "0.8.0" } rdf-trig "Deprecated. Trig RDFFormat" RDFFormat/TRIG)
(def-format ^{:deprecated "0.8.0" } rdf-trix "Deprecated. Trix RDFFormat" RDFFormat/TRIX)
(def-format ^{:deprecated "0.8.0" } rdf-xml "Deprecated. RDF-XML RDFFormat" RDFFormat/RDFXML)

(defmulti rdf-format->parser identity)

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
