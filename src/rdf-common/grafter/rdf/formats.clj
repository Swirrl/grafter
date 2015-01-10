(ns grafter.rdf.formats
  "Symbols used to specify different Linked Data Serializations."
  (:import [org.openrdf.rio RDFFormat]))

(def rdf-xml RDFFormat/RDFXML)

(def rdf-n3 RDFFormat/N3)

(def rdf-ntriples RDFFormat/NTRIPLES)

(def rdf-nquads RDFFormat/NQUADS)

(def rdf-turtle RDFFormat/TURTLE)

(def rdf-jsonld RDFFormat/JSONLD)

(def rdf-trix RDFFormat/TRIX)

(def rdf-trig RDFFormat/TRIG)
