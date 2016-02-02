# Grafter Release Notes

Copyright © 2014 Swirrl IT Ltd.

`VERSION: 0.7.0`

## 2016-02-02

- Update Sesame to 2.8.9 for RDF 1.1 support.
- Update Incanter to 1.5.7 to improve compatability with Clojure 1.7.0

`VERSION: 0.6.1`

## 2016-02-01

- Make grafter `ex-info` exceptions consistently have an `:error` key
  to identify them.  Prior to this we used bother `:error` and `:type`
  keys in `ex-data`

## 2016-01-22

`VERSION: 0.6.0`

- Remove `defpipe` & `defgraft`, instead use `declare-pipeline`
- Support many more types as arguments in pipelines / `lein-grafter`
  plugin, Numbers, URIs, URLs, UUIDs, Maps etc...
- Fix #47 preserve metadata set by adapters
- Updated dependencies (Sesame 2.7.16, Clojure 1.7.0, and others...)
- Bug fix: Coerce xsd:strings
- Use only quads - remove Triple record and add `triple=` function for comparing quads as triples
- Don't treat vectors as quads
- Fix bug with 1 arity `make-dataset`

## 2015-08-03
`VERSION: 0.5.1`

- Support reader and input-stream for read-dataset
- Support mime-types as :format parameters to read-dataset, read-datasets and write-dataset.

## 2015-06-26
`VERSION: 0.5.0`

- grafter-url library for building URLs
- `all-columns` removed
- Fixed issue #19 adjusting behaviour of `columns` to crop infinite sequences
- Make `columns` raise `IndexOutOfBoundsException` on unknown column names
- `make-dataset` doesnt infer column width from first row if f provided
- `make-dataset` preserves metadata if given a Dataset
- Serializing to quads with `add` supports overriding the graph
- Workaround minor Clojure compiler type-hint bug when using `->connection` with `with-open`
- Fix `rename-columns` to support renaming with an arbitrary rename function
- Fix bug in `melt` to work with string column names
- Make `->connection` a protocol
- Add support for previewing pretty printed graph templates

## 2015-03-05
`VERSION: 0.4.0`

- improved melt functions
- removed ontologies to grafter-vocabularies project
- setAutoCommit to false

## 2014-01-26
`VERSION 0.3`

## 2014-08-22
`VERSION: 0.2-SNAPSHOT (v0.2-rc1)`

A release candidate and Grafter's first external release.

- Internal tabular data representation ported to use Incanter
  Datasets.
- Documentation site grafter.org
- API documentation api.grafter.org

## 2014-07-23
`VERSION: 0.1.1-SNAPSHOT`

An internal release.  Used for some client work.

## 2014-07-03

`VERSION: 0.1`

An internal release.  Used for some client work.

## 2014-05-21

`VERSION: 0.1-SNAPSHOT`

An internal Swirrl prototype.
