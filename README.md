# Grafter

    "For the hard graft of linked data processing."

Grafter is a library, DSL and suite of tools for flexible, efficient,
ETL, data transformation and processing.  Its primary use is for
handling Linked Data conversions from tabular data formats into RDF
linked data format, but it is equally adept at handling tabular data
conversions.

See the official grafter website at [grafter.org](http://grafter.org/)
for more details.

For the Grafter rationale see our blog post:
[The hard graft of Linked Data ETL](http://blog.swirrl.com/articles/linked-data-etl/).

## What plans are there for Grafter?

Grafter is currently in the early stages of development, however we
[Swirrl](http://swirrl.com/) have been using it to transform
significant amounts of data for our clients within the government.

Grafter is currently an API and a small DSL for converting tabular
data into Linked Data.  However we have ambitious plans to develop a
suite of tools on top of it.  These tools are planned to include:

1. Command line tools for data processing.
1. Import services to load pipelines and execute predefined data
   transformations.
1. A Graphical ETL Tool to assist non-programmers in creating data
   transformation pipelines.

## Development

Grafter is deployed on the standard Clojure build repository
[Clojars](http://clojars.org/).

To use the Grafter API please add the following to your Clojure
projects `project.clj` file.  For more details on how to do this see
the [leiningen](http://leiningen.org/) build tool:

     [grafter/grafter "0.2-SNAPSHOT"]

Release candidates are released as `SNAPSHOT` builds and our first
official release will be `0.2`, this will hopefully be released in the
coming weeks.

We are currently following a `MAJOR.MINOR.PATCH` versioning scheme,
but are anticipating significant breaking API changes between minor
versions at least until we reach `1.0.0`.

`PATCH` versions should be close to being backwardly compatible with
previous `MINOR` versions.

## License

Copyright © 2014 Swirrl IT Ltd.

Distributed under the Eclipse Public License version 1.0, the same as
Clojure.
