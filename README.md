# Grafter - Linked Data & RDF Processing

[![Clojars Project](https://img.shields.io/clojars/v/io.github.swirrl/grafter.repository.svg)](https://clojars.org/io.github.swirrl/grafter.repository) | [![Clojars Project](https://img.shields.io/clojars/v/io.github.swirrl/grafter.io.svg)](https://clojars.org/io.github.swirrl/grafter.io) | [![Clojars Project](https://img.shields.io/clojars/v/io.github.swirrl/grafter.core.svg)](https://clojars.org/io.github.swirrl/grafter.core)

    "For the hard graft of linked data processing."

Grafter is a [Clojure](http://clojure.org/) library for linked data
processing.  It is mature and under active development.

It provides support for all common RDF serialisations and
includes a library of functions for querying and writing to SPARQL
repositories.

It is split into three sub-projects with their own dependency packages

- `io.github.swirrl/grafter.repository {:mvn/version "3.0.0"}` (SPARQL repositories via RDF4j) 
- `io.github.swirrl/grafter.io {:mvn/version "3.0.0"}` (Reading/Writing RDF formats via RDF4j)
- `io.github.swirrl/grafter.core {:mvn/version "3.0.0"}` (RDF protocols - independent of RDF4j)

## Prerequisites

- Java 17
- Clojure 1.11.1

## FAQ

*Where can I find the api-docs?*

[Latest docs](http://swirrl.github.io/grafter)

Legacy docs [api.grafter.org](http://api.grafter.org/)

*Didn't grafter also contain tools for tabular processing?*

As of 0.9.0 the `grafter.tabular` library has been moved into a
[separate repository](https://github.com/Swirrl/grafter.tabular) so
the core grafter library can focus on processing linked data.

This part of the library is now considered deprecated.  If you depend
on it you can still use it, and it may receive occaisional
maintainance updates.

If you're looking to start a greenfield project then you can easily
wire up any capable CSV/excel parser to the RDF processing side of
grafter.

## License

Copyright © 2014 Swirrl IT Ltd.

Distributed under the Eclipse Public License version 1.0, the same as
Clojure.
