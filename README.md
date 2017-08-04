# Grafter - Linked Data & RDF Processing

    "For the hard graft of linked data processing."

Grafter is a [Clojure](http://clojure.org/) library for linked data
processing.  It is mature and under active development.

It provides support for all common RDF serialisations and
includes a library of functions for querying and writing to SPARQL
repositories.

## FAQ

*Where can I find the api-docs?*

[api.grafter.org](http://api.grafter.org/)

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
