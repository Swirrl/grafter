# Grafter SPARQL Repository

This small piece of Java code enhances the SPARQL Repository to behave how we
want it to when talking to SPARQL endpoints, such as Stardog.

It's main job is to centralise how we handle timeouts, query parameters, etc...

## Building

**DON'T BUILD ME!!!**

We never build the java project as a separate artifact, and instead
just use the `pom.xml` to provide IDE code completion.

This sub project should be built by running `lein uberjar` from the
top level grafter project directory.

/NOTE/ This project `pom.xml` should be kept in sync with the
corresponding sesame dependency mentioned in the top level `project.clj`.
