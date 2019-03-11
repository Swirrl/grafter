(ns grafter.rdf4j
  (:require [grafter.rdf4j.io :as io]
            [grafter.core :as core]))

(defn ^:no-doc statements
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
  URI's on a new prefix when reading."
  [this & {:keys [format buffer-size base-uri] :as options}]
  (core/to-statements this options))
