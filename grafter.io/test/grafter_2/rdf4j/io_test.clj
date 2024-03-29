(ns grafter-2.rdf4j.io-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer :all]
            [grafter-2.rdf.protocols :as pr :refer [->Quad add literal]]
            [grafter-2.rdf4j.io :as sut :refer [statements]]
            [grafter-2.rdf4j.templater :refer [graph]]
            [grafter.url :as url]
            [grafter.vocabularies.core :refer [prefixer ->uri]])
  (:import grafter_2.rdf.protocols.OffsetDate
           java.net.URI
           [java.time LocalDate LocalDateTime LocalTime OffsetDateTime OffsetTime ZoneOffset]
           [org.eclipse.rdf4j.model Literal]
           [org.eclipse.rdf4j.model.impl SimpleValueFactory]))


(def ^:private value-factory (SimpleValueFactory/getInstance))

(deftest round-trip-numeric-types-test
  (are [xsd type number]
       (is (= number (pr/raw-value (sut/->backend-type (pr/->grafter-type (.createLiteral value-factory
                                                                                          number
                                                                                          (.createIRI value-factory xsd)))))))

    "http://www.w3.org/2001/XMLSchema#byte" Byte "10"
    "http://www.w3.org/2001/XMLSchema#short" Short "10"
    "http://www.w3.org/2001/XMLSchema#decimal" java.math.BigDecimal "10"
    "http://www.w3.org/2001/XMLSchema#double" Double "10.7"
    "http://www.w3.org/2001/XMLSchema#float" Float "10.6"
    "http://www.w3.org/2001/XMLSchema#integer" BigInteger "10"
    "http://www.w3.org/2001/XMLSchema#long" Long "10"
    "http://www.w3.org/2001/XMLSchema#int" Integer "10"))

(deftest backend-literal->grafter-type-test
  (are [clj-val uri klass]
      (let [ret-val (sut/backend-literal->grafter-type (sut/->backend-type clj-val))]
        (is (= clj-val ret-val))
        (is (= klass (class clj-val)))
        (is (= (->uri uri) (pr/datatype-uri clj-val))))

      true           "http://www.w3.org/2001/XMLSchema#boolean" Boolean
      (byte 10)      "http://www.w3.org/2001/XMLSchema#byte" Byte
      (short 12)     "http://www.w3.org/2001/XMLSchema#short" Short
      (bigdec 9)     "http://www.w3.org/2001/XMLSchema#decimal" java.math.BigDecimal
      (double 33.33) "http://www.w3.org/2001/XMLSchema#double" Double
      (float 23.8)   "http://www.w3.org/2001/XMLSchema#float" Float
      10             "http://www.w3.org/2001/XMLSchema#long" Long

      ;; Yes this is correct according to the XSD spec. #integer is
      ;; unbounded whereas #int is bounded
      (bigint 3)     "http://www.w3.org/2001/XMLSchema#integer" clojure.lang.BigInt
      (int 42)       "http://www.w3.org/2001/XMLSchema#int" Integer
      "hello"        "http://www.w3.org/2001/XMLSchema#string" String

      (OffsetDateTime/of (LocalDate/of 2018 11 3)
                         (LocalTime/of 11 13 15 300)
                         (ZoneOffset/ofHoursMinutes 4 15))
      "http://www.w3.org/2001/XMLSchema#dateTime"
      OffsetDateTime

      (LocalDateTime/of (LocalDate/of 2018 11 3)
                        (LocalTime/of 11 13 15 300))
      "http://www.w3.org/2001/XMLSchema#dateTime"
      LocalDateTime

      (LocalDate/of 2018 11 3)
      "http://www.w3.org/2001/XMLSchema#date"
      LocalDate

      (LocalTime/of 11 13 15 300)
      "http://www.w3.org/2001/XMLSchema#time"
      LocalTime))

(def data (prefixer "http://grafter/data/"))

(def date-local (data "date-local"))
(def date-utc (data "date-utc"))
(def date-offset (data "date-offset"))
(def date-time-local (data "date-time-local"))
(def date-time-local-with-half-second (data "date-time-local-with-half-second"))
(def date-time-with-offset (data "date-time-with-offset"))
(def date-time-utc (data "date-time-utc"))

(def time-local (data "time-local"))
(def time-local-with-fraction-of-second (data "time-local-with-fraction-of-second"))
(def time-local-midnight-00 (data "time-local-midnight-00"))
(def time-local-midnight-24 (data "time-local-midnight-24"))

(def time-local-with-insane-precision (data "time-local-with-insane-precision"))

(def time-utc (data "time-utc"))


(deftest java-time-api-coercions
  (let [test-cases (let [triples (->> (io/resource "grafter/rdf4j/dates-and-times.ttl")
                                      statements)]
                     (zipmap (map :s triples)
                             (map :o triples)))]

    (is (= (LocalDate/of 1970 1 1) (test-cases date-local)))

    ;; We explicitly need to support zoned dates without coercing to dateTimes!
    (is (instance? grafter_2.rdf.protocols.OffsetDate (test-cases date-utc)))

    (is (= (pr/->OffsetDate (LocalDate/of 1970 1 1)
                            (ZoneOffset/of "Z")) (test-cases date-utc)))

    (is (= (pr/->OffsetDate (LocalDate/of 1970 1 1)
                            (ZoneOffset/ofHours -5)) (test-cases date-offset)))

    (is (= (LocalTime/of 13 20)
           (test-cases time-local)))

    (is (= (LocalTime/of 13 20 30 555000000)
           (test-cases time-local-with-fraction-of-second)))

    (is (= (LocalTime/of 0 0)
           (test-cases time-local-midnight-24)
           (test-cases time-local-midnight-00)))

    (is (= (LocalTime/of 13 20 30 999999999)
           (test-cases time-local-with-insane-precision))
        "Truncated to 9 decimal places.  Technically this is not correct as xsd:time should be infinite/arbitrary precision")

    (is (= (OffsetTime/of (LocalTime/of 13 20)
                          (ZoneOffset/of "Z"))
           (test-cases time-utc)))

    (is (= (LocalDateTime/of 2004 4 12 13 20 0 0)
           (test-cases date-time-local)))

    (is (= (LocalDateTime/of 2004 4 12 13 20 15 500000000)
           (test-cases date-time-local-with-half-second)))

    (is (= (OffsetDateTime/of (LocalDateTime/of 2004 4 12 13 20 0 0)
                              (ZoneOffset/of "-05:00"))
           (test-cases date-time-with-offset)))

    (is (= (OffsetDateTime/of (LocalDateTime/of 2004 4 12 13 20 0 0)
                              (ZoneOffset/of "Z"))
           (test-cases date-time-utc)))))

(deftest round-trip-times
  (testing "Times, Dates and Date Times round trip"
    ;; NOTE this is not purely lossless.  Some types coerce with some
    ;; truncation; for example we do not support infinite precision on
    ;; times.
    ;;
    ;; So there is an implicit normalisation step that happens by
    ;; loading data.  However data that is loaded and written through
    ;; grafter after the implicit normalisation should round trip.
    (let [triples (->> (io/resource "grafter/rdf4j/dates-and-times.ttl")
                       statements)

          loaded-cases (zipmap (map :s triples)
                               (map :o triples))]

      (doseq [[uri loaded-test-case] loaded-cases]
        (is (= (grafter-2.rdf.protocols/->grafter-type loaded-test-case)
               loaded-test-case)
            "Grafter types all convert to themselves")


        (is (= (pr/->grafter-type (sut/->backend-type loaded-test-case))
               loaded-test-case)
            uri)))))

(deftest literal-datatype->type-special-floating-values-test
  (is (Double/isNaN (sut/backend-literal->grafter-type (literal "NaN" "http://www.w3.org/2001/XMLSchema#double"))))
  (is (= Double/POSITIVE_INFINITY (sut/backend-literal->grafter-type (literal "INF" "http://www.w3.org/2001/XMLSchema#double"))))
  (is (= Double/POSITIVE_INFINITY (sut/backend-literal->grafter-type (literal "+INF" "http://www.w3.org/2001/XMLSchema#double"))))
  (is (= Double/NEGATIVE_INFINITY (sut/backend-literal->grafter-type (literal "-INF" "http://www.w3.org/2001/XMLSchema#double"))))

  (is (Float/isNaN (sut/backend-literal->grafter-type (literal "NaN" "http://www.w3.org/2001/XMLSchema#float"))))
  (is (= Float/POSITIVE_INFINITY (sut/backend-literal->grafter-type (literal "INF" "http://www.w3.org/2001/XMLSchema#float"))))
  (is (= Float/POSITIVE_INFINITY (sut/backend-literal->grafter-type (literal "+INF" "http://www.w3.org/2001/XMLSchema#float"))))
  (is (= Float/NEGATIVE_INFINITY (sut/backend-literal->grafter-type (literal "-INF" "http://www.w3.org/2001/XMLSchema#float")))))


(deftest language-string-test
  (let [bonsoir (pr/language "Bonsoir Mademoiselle" :fr)]
    (is (= bonsoir (sut/backend-literal->grafter-type bonsoir)))
    (is (= bonsoir (pr/->grafter-type (sut/->backend-type bonsoir))))))

(deftest literal-test
  (is (instance? Literal (sut/->backend-type (pr/literal "2014-01-01" (java.net.URI. "http://www.w3.org/2001/XMLSchema#date"))))))

(deftest round-trip-quad-test
  (testing "round trips"
    (testing "quad"
      (let [quad (->Quad (url/->java-uri "http://example.org/test/subject")
                         (url/->java-uri "http://example.org/test/predicate")
                         (url/->java-uri "http://example.org/test/object")
                         (url/->java-uri "http://example.org/test/graph"))]
        (is (= quad
               (sut/backend-quad->grafter-quad (sut/quad->backend-quad quad))))))

    (testing "quad with bnode"
      (let [quad (->Quad (url/->java-uri "http://example.org/test/subject")
                         (url/->java-uri "http://example.org/test/predicate")
                         (pr/make-blank-node :id-1)
                         (url/->java-uri "http://example.org/test/graph"))]
        (is (= quad
               (sut/backend-quad->grafter-quad (sut/quad->backend-quad quad))))))

    (testing "triple (nil graph)"
      (let [quad (->Quad (url/->java-uri "http://example.org/test/subject")
                         (url/->java-uri "http://example.org/test/predicate")
                         (url/->java-uri "http://example.org/test/object")
                         nil)]
        (is (= quad
               (sut/backend-quad->grafter-quad (sut/quad->backend-quad quad))))))))

(deftest round-trip-quad-serialize-deserialize-test
  (let [quad (graph (url/->java-uri "http://example.org/test/graph")
                    [(url/->java-uri "http://test/subj") [(url/->java-uri "http://test/pred") (url/->java-uri "http://test/obj")]])
        string-wtr (java.io.StringWriter.)
        serializer (sut/rdf-writer string-wtr :format :nq)]
    (add serializer quad)

    (let [output-str (str string-wtr)]
      (with-open [rdr (java.io.StringReader. output-str)]
        (is (= quad
               (statements rdr :format :nq)))))))

(deftest binary-rdf-test
  (testing "round trip quads via binary RDF"
    (let [baos (java.io.ByteArrayOutputStream. 8192)
          quads (graph (url/->java-uri "http://example.org/test/graph")
                       [(url/->java-uri "http://test/subj") [(url/->java-uri "http://test/pred") (url/->java-uri "http://test/obj")]])]

      (add (sut/rdf-writer baos :format :brf) quads)

      (let [bais (java.io.ByteArrayInputStream. (.toByteArray baos))]
        (is (= (statements bais :format :brf)
               quads))))))


(deftest quad->backend-quad-test
  (testing "IStatement->sesame-statement"
    (is (= (sut/quad->backend-quad (->Quad (url/->java-uri "http://foo.com/") (url/->java-uri "http://bar.com/") "a string" (url/->java-uri "http://blah.com/")))
           (.createStatement value-factory
                             (.createIRI value-factory "http://foo.com/")
                             (.createIRI value-factory "http://bar.com/")
                             (.createLiteral value-factory "a string")
                             (.createIRI value-factory "http://blah.com/"))))

    (testing "Raising Exceptions"
      (let [broken-quad (with-meta (->Quad nil "http://bar.com/" "http://baz.com/" "http://blah.com/") {:foo :bar})
            ex (ex-data (is (thrown? clojure.lang.ExceptionInfo
                                     (sut/quad->backend-quad broken-quad))))]

        (is (= (->Quad
                nil
                "http://bar.com/"
                "http://baz.com/"
                "http://blah.com/") (:quad ex))
            "Adds the statement itself to the exception data")

        (is (= {:foo :bar} (:quad-meta ex))
            "Metadata from quads is reported in exception data")))))

(deftest to-grafter-url-protocol-test
  (testing "extends RDF Model URI"
    (let [uri (.createIRI value-factory "http://www.tokyo-3.com:777/ayanami?geofront=retracted")
          grafter-url (url/->grafter-url uri)]
      (are [expected actual] (= expected actual)
                             777 (url/port grafter-url)
                             "www.tokyo-3.com" (url/host grafter-url)
                             "http" (url/scheme grafter-url)
                             ["ayanami"] (url/path-segments grafter-url)))))

(deftest blank-nodes-load-test
  (testing "Blank nodes are blank node objects"
    (let [[[s1 p1 o1] [s2 p2 o2]] (statements (io/resource "grafter/rdf/bnodes.nt"))]
      (is (pr/blank-node? o1))
      (is (pr/blank-node? s2)))))


(def rdf-data-1 [(pr/->Triple (java.net.URI. "http://foo.bar/baz") (java.net.URI. "http://p") (java.net.URI. "http://o"))])
(def rdf-data-2 [(pr/->Triple (java.net.URI. "http://foo.bar/qux") (java.net.URI. "http://p") (java.net.URI. "http://o"))])
(def rdf-data-3 [(pr/->Triple (java.net.URI. "http://barbar/baz") (java.net.URI. "http://p") (java.net.URI. "http://o"))])


(deftest rdf-writer-rdf4j-handler-operation-ordering
  (testing "Supported orderings of operations"
    (with-open [sw (java.io.StringWriter.)]
      (let [;; rdf-writer internally calls .startRDF followed by write-prefixes
            tw (sut/rdf-writer sw {:format :ttl :prefixes {"foobar" "http://foo.bar/"}})]

        ;; because former rdf-writer operation calls .startRDF we
        ;; can then immediately add data. Calls to add will always
        ;; flush the stream but not close the underlying writer.
        (pr/add tw rdf-data-1)

        (is (= "@prefix foobar: <http://foo.bar/> .\n\nfoobar:baz <http://p> <http://o> .\n"
               (str sw)))

        ;; Call again to show we can keep adding even though add
        ;; internally calls .endRDF (flush).
        (pr/add tw rdf-data-2)

        (is (= "@prefix foobar: <http://foo.bar/> .\n\nfoobar:baz <http://p> <http://o> .\n\nfoobar:qux <http://p> <http://o> .\n"
               (str sw)))

        ;; Show we can even write prefixes in the middle of the stream
        (sut/write-prefixes tw {"barbar" "http://barbar/"})

        ;; Theres no point writing prefixes in the middle of a stream
        ;; unless we follow with more data (that might use them),
        ;; so...
        (pr/add tw rdf-data-3)

        (is (= (str "@prefix foobar: <http://foo.bar/> .\n\nfoobar:baz <http://p> <http://o> .\n\nfoobar:qux <http://p> <http://o> .\n"
                    "@prefix barbar: <http://barbar/> .\n\nbarbar:baz <http://p> <http://o> .\n")
               (str sw)))))))

(deftest make-rdf-writer-rdf4j-handler-operation-ordering
  (testing "Supported orderings of operations"
    (with-open [sw (java.io.StringWriter.)]
      (let [;; rdf-writer internally calls .startRDF followed by write-prefixes
            tw (sut/make-rdf-writer sw {:format :ttl})]

        ;; NOTE this deftest is functionally equivalent to
        ;; rdf-writer-rdf4j-handler-operation-ordering, but shows the
        ;; decomplected calls... make-rdf-writer does not call
        ;; .startRDF or write-prefixes

        (.startRDF tw)
        (sut/write-prefixes tw {"foobar" "http://foo.bar/"})

        ;; because former rdf-writer operation calls .startRDF we
        ;; can then immediately add data. Calls to add will always
        ;; flush the stream but not close the underlying writer.
        (pr/add tw rdf-data-1)

        (is (= "@prefix foobar: <http://foo.bar/> .\n\nfoobar:baz <http://p> <http://o> .\n"
               (str sw)))

        ;; Call again to show we can keep adding even though add
        ;; internally calls .endRDF (flush).
        (pr/add tw rdf-data-2)

        (is (= "@prefix foobar: <http://foo.bar/> .\n\nfoobar:baz <http://p> <http://o> .\n\nfoobar:qux <http://p> <http://o> .\n"
               (str sw)))

        ;; Show we can even write prefixes in the middle of the stream
        (sut/write-prefixes tw {"barbar" "http://barbar/"})

        ;; Theres no point writing prefixes in the middle of a stream
        ;; unless we follow with more data (that might use them),
        ;; so...
        (pr/add tw rdf-data-3)

        (is (= (str "@prefix foobar: <http://foo.bar/> .\n\nfoobar:baz <http://p> <http://o> .\n\nfoobar:qux <http://p> <http://o> .\n"
                    "@prefix barbar: <http://barbar/> .\n\nbarbar:baz <http://p> <http://o> .\n")
               (str sw))))))

  (testing "Assertions about RDF4j"
    (testing "Unsupported operation orderings"
      ;; NOTE these are really here to capture and assert RDF4j
      ;; semantics (as they've changed in the past)

      (with-open [sw (java.io.StringWriter.)]
        (let [tw (sut/make-rdf-writer sw {:format :ttl})]

          (is (thrown? Exception
                       (.startRDF tw)
                       (.startRDF tw))
              ".startRDF in RDF4j raises an exception when called more than once"))

        (let [tw (sut/make-rdf-writer sw {:format :ttl})]

          (is (thrown? Exception
                       (sut/write-prefixes tw {"foo" "http://foo/bar"}))
              "Can't write prefixes without .startRDF being called first."))

        (let [tw (sut/make-rdf-writer sw {:format :ttl})]
          (is (thrown? Exception
                       (.endRDF tw))
              "Can't call .endRDF before .startRDF"))

        (let [tw (sut/make-rdf-writer sw {:format :ttl})]
          (is (thrown? Exception
                       (.endRDF tw))
              "Can't call .endRDF before .startRDF"))

        (testing "Calling .endRDF multiple times doesn't actually prevent subsequent use"
          (with-open [sw (java.io.StringWriter.)]
            (let [tw (sut/make-rdf-writer sw {:format :ttl})]
              (.startRDF tw)
              (add tw rdf-data-1)
              (.endRDF tw)
              (.endRDF tw)
              (add tw rdf-data-2)

              (is (= "\n<http://foo.bar/baz> <http://p> <http://o> .\n\n<http://foo.bar/qux> <http://p> <http://o> .\n"
                     (str sw))))))))))
