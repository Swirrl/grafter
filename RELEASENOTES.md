# Grafter Release Notes

Copyright © 2014 Swirrl IT Ltd.

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
