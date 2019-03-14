(ns ^{:added "0.12.1"}
    grafter-2.rdf4j.repository.registry
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
  (:require [clojure.string :as str])
  (:import [java.nio.charset Charset]
           [org.eclipse.rdf4j.rio RDFParserRegistry RDFFormat]
           [org.eclipse.rdf4j.query.resultio TupleQueryResultFormat BooleanQueryResultFormat
            TupleQueryResultParserRegistry
            BooleanQueryResultParserRegistry]
           [org.eclipse.rdf4j.query.resultio.text.csv SPARQLResultsCSVParserFactory]))


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
  (let [setify-factories (fn [r] (->> r
                                     .getKeys
                                     (map #(class (.orElse (.get r %) nil)))
                                     set))]
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

  {:select #{org.eclipse.rdf4j.query.resultio.sparqlxml.SPARQLResultsXMLParserFactory
             org.eclipse.rdf4j.query.resultio.text.csv.SPARQLResultsCSVParserFactory
             org.eclipse.rdf4j.query.resultio.sparqljson.SPARQLResultsJSONParserFactory
             org.eclipse.rdf4j.query.resultio.text.tsv.SPARQLResultsTSVParserFactory
             org.eclipse.rdf4j.query.resultio.binary.BinaryQueryResultParserFactory},
   :construct #{org.eclipse.rdf4j.rio.ntriples.NTriplesParserFactory
                org.eclipse.rdf4j.rio.trix.TriXParserFactory
                org.eclipse.rdf4j.rio.jsonld.JSONLDParserFactory
                org.eclipse.rdf4j.rio.trig.TriGParserFactory
                org.eclipse.rdf4j.rio.binary.BinaryRDFParserFactory
                org.eclipse.rdf4j.rio.n3.N3ParserFactory
                org.eclipse.rdf4j.rio.rdfjson.RDFJSONParserFactory
                org.eclipse.rdf4j.rio.rdfxml.RDFXMLParserFactory
                org.eclipse.rdf4j.rio.nquads.NQuadsParserFactory},
   :ask #{org.eclipse.rdf4j.query.resultio.sparqljson.SPARQLBooleanJSONParserFactory
          org.eclipse.rdf4j.query.resultio.text.BooleanTextParserFactory
          org.eclipse.rdf4j.query.resultio.sparqlxml.SPARQLBooleanXMLParserFactory}}


  ;; Once you have the datastructure above, you can adjust it as
  ;; desired and reset the registries e.g.

  (let [updated-registries (update (registered-parser-factories)
                                   ;; Force removal of TurtleParser
                                   :construct #(disj % org.eclipse.rdf4j.rio.turtle.TurtleParserFactory))]

    ;; reset the registered parsers
    (register-parser-factories! updated-registries))

  )
