(ns grafter.rdf.repository.registry
  "Namespace containing functions to manage the global registry of
  Sesame parsers.

  Normally you should not need to adjust the default global
  registries, however if you need tighter control over what
  serialisation format is negotiated on a SPARQL endpoint; for example
  if the endpoint you are talking to has a serialisation bug then you
  can force the use of different parser by removing the problematic
  one.

  Be warned though, these registries apply globally (process wide), so
  altering them may have unintended consequences."
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import [java.nio.charset Charset]
           [org.openrdf.rio RDFParserRegistry RDFFormat]
           [org.openrdf.query.resultio TupleQueryResultFormat BooleanQueryResultFormat
            TupleQueryResultParserRegistry
            BooleanQueryResultParserRegistry]
           [org.openrdf.query.resultio.text.csv SPARQLResultsCSVParserFactory]))


(defn parser-registries
  "Returns a map of the low level sesame parser registries associated
  with their corresponding query-type. You should normally call
  registered-parser-factories which will return you a representation
  of this information."
  []
  {:select (TupleQueryResultParserRegistry/getInstance)
   :construct (RDFParserRegistry/getInstance)
   :ask (BooleanQueryResultParserRegistry/getInstance)})

(defn registered-parser-factories
  "Returns the sesame registered query parser factories associated
  with their corresponding query-type.

  These factories define the parsers used for content negotiation
  inside sesame."
  []
  (let [setify-factories (fn [r] (set (map #(class (.get r %)) (.getKeys r))))]
    (apply merge
           (for [[q-type reg] (parser-registries)]
             {q-type (setify-factories reg)}))))

(defn register-parser-factory!
  "Takes a query-type keyword and a parser factory class and registers
  it with sesame."
  [query-type factory-klass]
  (let [registry (query-type (parser-registries))]
    (.add registry (.newInstance factory-klass))))

(defn clear-registry!
  "Clears all registered query-parsers for the specified query-type."
  [query-type]
  (let [registry (query-type (parser-registries))]
    (doseq [pf (seq (.getAll registry))]
      (.remove registry pf))))

(defn register-parser-factories!
  "Takes a map from query-type (:select :construct or :ask) to the
  sesame Parser Factory class and registers the parsers with the
  appropriate sesame registries.

  This works with the same data representation returned by
  registered-parser-factories."
  [reg-data]
  (let [registries (parser-registries)]
    (doseq [[query-type reg] registries]
      (clear-registry! query-type))

    (doseq [[query-type factories] reg-data
            factory factories]
      (register-parser-factory! query-type factory))))

(comment
  ;; usage examples

  ;; Use registered-parser-factories to query the global registry and
  ;; return the set of registered parsers for each query type

  (registered-parser-factories) ;; =>

  {:select #{org.openrdf.query.resultio.sparqlxml.SPARQLResultsXMLParserFactory
             org.openrdf.query.resultio.text.csv.SPARQLResultsCSVParserFactory
             org.openrdf.query.resultio.sparqljson.SPARQLResultsJSONParserFactory
             org.openrdf.query.resultio.text.tsv.SPARQLResultsTSVParserFactory
             org.openrdf.query.resultio.binary.BinaryQueryResultParserFactory},
   :construct #{org.openrdf.rio.ntriples.NTriplesParserFactory
                org.openrdf.rio.trix.TriXParserFactory
                org.openrdf.rio.jsonld.JSONLDParserFactory
                org.openrdf.rio.trig.TriGParserFactory
                org.openrdf.rio.binary.BinaryRDFParserFactory
                org.openrdf.rio.n3.N3ParserFactory
                org.openrdf.rio.rdfjson.RDFJSONParserFactory
                org.openrdf.rio.rdfxml.RDFXMLParserFactory
                org.openrdf.rio.nquads.NQuadsParserFactory},
   :ask #{org.openrdf.query.resultio.sparqljson.SPARQLBooleanJSONParserFactory
          org.openrdf.query.resultio.text.BooleanTextParserFactory
          org.openrdf.query.resultio.sparqlxml.SPARQLBooleanXMLParserFactory}}


  ;; Once you have the datastructure above, you can adjust it as
  ;; desired and reset the registries e.g.

  (let [updated-registries (update (registered-parser-factories)
                                   ;; Force removal of TurtleParser
                                   :construct #(disj % org.openrdf.rio.turtle.TurtleParserFactory))]

    ;; reset the registered parsers
    (register-parser-factories! updated-registries))

  )
