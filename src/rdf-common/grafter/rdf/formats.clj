(ns grafter.rdf.formats
  "Symbols used to specify different Linked Data Serializations."
  (:import [org.openrdf.rio RDFFormat]))


(defmulti coerce-media-type
  "Extensible multimethod that accepts a mime-type string and returns
  the appropriate sesame RDFFormat object.

  NOTE: the coerce-format function also uses this, and supports both
  mime-types and file extensions." identity)

(defmethod coerce-media-type :default [fmt]
  nil)

(def coerce-format nil)

(defmulti coerce-format
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
          :else fmt)))

(defmethod coerce-format String [fmt]
  (if (re-find #"/" fmt)
    (coerce-media-type fmt)
    (coerce-format (keyword fmt))))

(defmethod coerce-format ::coerced [fmt] fmt)

(defmethod coerce-format :default [fmt]
  nil)

(defmacro def-format
  "Define a bunch of format coercions from mime-type and file
  extension to RDFFormat.  This macro inspects the RDFFormat object it
  is given and generates appropriate multi-methods to do the coercion
  for you.

  If you want to define new coercions custom coercions (not baked into
  sesame) you can extend the coerce-format multimethod directly."
  [sym docstr format]
  `(do
     (def ~sym ~docstr ~format)

     (doseq [ext# (map keyword (.getFileExtensions ~format))]
       (defmethod coerce-format ext# [foo#] ~format))

     (doseq [mt# (.getMIMETypes ~format)]
       (defmethod coerce-media-type mt# [foo#] ~format))))

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
