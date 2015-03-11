(ns grafter.rdf.formats
  "Symbols used to specify different Linked Data Serializations."
  (:import [org.openrdf.rio RDFFormat]))

(def ^{:doc "RDF XML Serialisation."} rdf-xml RDFFormat/RDFXML)

(def ^{:doc "N3 RDF Serialisation."} rdf-n3 RDFFormat/N3)

(def ^{:doc "N-triples RDF Serialisation."} rdf-ntriples RDFFormat/NTRIPLES)

(def ^{:doc "NQuads RDF Serialisation."} rdf-nquads RDFFormat/NQUADS)

(def ^{:doc "Turtle RDF Serialisation."} rdf-turtle RDFFormat/TURTLE)

(def ^{:doc "JSON-LD RDF Serialisation."} rdf-jsonld RDFFormat/JSONLD)

(def ^{:doc "Trix RDF Serialisation."} rdf-trix RDFFormat/TRIX)

(def ^{:doc "Trig RDF Serialisation."} rdf-trig RDFFormat/TRIG)
