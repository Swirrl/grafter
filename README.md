#Grafter

Exploring the use of Clojure for RDFizing, DSL's, RDF tooling and more
tightly integrating with triple stores.

Grafter has a number of stated aims:

1) To show that Clojure could provide us with a number of platform
   benefits:

   - Easy access to Java tooling & API's, including the ability to
     talk direct to underlying triple store implementations.
   - Simplified infrastructure due to excellent concurrency support
     etc.
   - Potentially better performance by
     + Utilising local disk backed, indexed triple stores with no
       additional infrastructure overhead
     + Using a more performant language
     + Potentially skipping unnecessary serialization/parsing steps
   - Being able to create and manage logical graphs/endpoints more
     easily.

2) To show that Clojure is a near perfect tool for building an
   RDFization DSL which has a number of desirable properties that
   would be harder to achieve with more conventional approaches.

This document tries to illustrate the DSL with a worked example,
whilst the [Grafter Core Concepts & Semantics](https://github.com/Swirrl/rdfizing-grafter-clj/blob/master/doc/grafter-core-concepts.org) document tries to work
through some of the ideas in a little more depth.

##RDFizing

In terms of RDFization the core idea is:

1) To build up a decent library of low level functions for common
   RDFing tasks.

2) To build a DSL with a simplified, consistent syntax that can be
   easily targetted by tooling etc...  The idea here is that the DSL's
   syntax will be built purely in terms of [Extensible Data Notation](https://github.com/edn-format/edn).

   (EDN is sort of like JSON, except it's syntactically Clojure,
   supports more data types and collections, and is extensible.
   API's for parsing and writing EDN are available in lots of
   languages including [Ruby](https://github.com/relevance/edn-ruby) and [Javascript](https://github.com/shaunxcode/jsedn)

   Above the syntactic layer Grafter should try and provide a language
   of operations that are simple enough for basic end user tooling,
   whilst ensuring sane (non-turing complete) semantics.

   As the grafter DSL snippets will also be valid Clojure programs,
   DSL forms will be literally copy/pastable inside arbitrary Clojure
   programs.

3) Grafter should aim to be a componentized workflow language, where
   logging, error reporting and more advanced features such as
   debugging, tracing and the configuration of concrete
   implementations can be easily applied by either the environment or
   a user to arbitrary pipelines without having to modify the pipeline
   itself.

We refer to the lower-level of grafter as the Grafter API and the
higher level as the grafter DSL.  Sometimes functions may be both part
of the DSL and the API so the distinction is not necessarily always
clear cut.

##Worked Example

Grafter can be thought of a number of separate DSLs that are each
targeted at specialised tasks that can then be seamlessly tied
together.

The main DSL's are:

- The Cell Conversion DSL :: A mini DSL of composable functions, and
     higher-order functions which take a single argument and validate
     and convert its type.
- The Table Operation DSL :: This DSL tries to map cleanly to
     intuitive spreadsheet like operations.  The idea is that the bulk
     of the transformation and wiring required can be done here.
- Triple Templates DSL :: This DSL receives each processed row as an
     argument, and returns a sequence of Statements.
- Filtering & Validation DSL :: This is pretty much absent apart from
     a filtering clause at this stage.

###User Definable Functions

It is envisaged that users will need to define their own functions.

Firstly a UI could be built that would allow simple mapping functions
to be defined.  For example URIifying facility types can be expressed
like this with the urban prefixer:

``` clojure
(def uriify-facility {"Museums" (urban "Museum")
                      "Arts" (urban "ArtsCentre")
                      "Community Facility" (urban "CommunityFacility")
                      "Libraries" (urban "Library")
                      "Music" (urban "MusicVenue")
                      "Sport Centres" (urban "SportsCentre")})
```

At the next level of complexity, users can supply their own javascript
functions which can be used within the pipeline.  These functions are
compiled and wrapped with a small Clojure function so they can be used
seamlessly in the pipeline.

``` clojure
(def slugify-facility
  (js-fn "function(name) {
              var lower = name.toLowerCase();
              return lower.replace(/\\ /g, '-');
         }"))
```

Finally we can obviously also define functions in Clojure:

``` clojure
(defn date-slug [date]
  (str (.getYear date) "-" (.getMonthOfYear date) "/"))
```

###Extract : Cell Conversion DSL

This DSL is currently monadic in order to allow uniform (swapable
logic) to occur between the functions being composed.  The main
utility of this right now is that it means you can more easily import
standard clojure/java functions into the API and adapt them
independently to the cell parsing environment.  For example parse-int
does not need to implement error handling for empty strings etc, as
that can be provided by the monad (here blank-m).

I am still a little unsure about this part of the API, but it works
quite well.

Things to note about it:

- Each function takes one argument
- Each line binds a name to a function.
- Functions are composed pointfree (the argument passing is invisible)
- Sometimes we call a "function factory" to build us a function, to do
  the job we want, e.g. replacing "," with the empty string, or
  mapping the empty string to "0".
- You can adjust what happens between functions such as the error
  handling strategy by wrapping it with a different monad.  The
  closest binding scope wins.

Future ideas:

- We could make the DSL more terse and expressive by effectively also
  using these function names to identify the columns in the DSL.
- You can interpret these functions in several ways depending on
  context, i.e. you could run them across the cells to display
  errors/warnings or by swapping the monad you could use the same
  functions to do the type conversions as we do here.

``` clojure
  (with-monad blank-m
    (let [rdfstr                    (lift-1 (fn [str] (s str :en)))
          replace-comma             (lift-1 (replacer "," ""))
          trim                      (lift-1 clojure.string/trim)
          parse-attendance          (with-monad identity-m (m-chain [(lift-1 (mapper {"" "0"}))
                                                                     replace-comma
                                                                     trim
                                                                     parse-int]))
          parse-year                (m-chain [trim replace-comma parse-int])
          convert-month             (m-chain [trim
                                              (lift-1 clojure.string/lower-case)
                                              (lift-1 {"january" 1 "jan" 1 "1" 1
                                                       "february" 2 "feb" 2 "2" 2
                                                       "march" 3 "mar" 3 "3" 3
                                                       "april" 4 "apr" 4 "4" 4
                                                       "may" 5 "5" 5
                                                       "june" 6 "jun" 6 "6"  6
                                                       "july" 7 "jul" 7 "7"  7
                                                       "august" 8 "aug" 8 "8" 8
                                                       "september" 9 "sep" 9 "sept" 9 "9"  9
                                                       "october" 10 "oct" 10 "10" 10
                                                       "november" 11 "nov" 11 "11" 11
                                                       "december" 12 "dec" 12 "12" 12
                                                       })])
          convert-year              (m-chain [trim parse-int date-time])
          address-line              (m-chain [trim rdfstr])
          city                      (m-chain [trim rdfstr])
          post-code                 (m-chain [trim rdfstr])
          uriify-pcode              (m-chain [trim
                                              (lift-1 (replacer " " ""))
                                              (lift-1 clojure.string/upper-case)
                                              (lift-1 (prefixer "http://data.ordnancesurvey.co.uk/id/postcodeunit/"))])
          url                       (lift-1 #(java.net.URL. %))

          prefix-monthly-attendance (m-chain [(lift-1 date-slug)
                                              (lift-1 (prefixer "/community-facility/"))])
          prefix-facility           (prefixer "http://linked.glasgow.gov.uk/data/facility_attendance")]

  ;; table conversion code here.
  ))
```

###Transform : Table conversion DSL

The code below is part of my original CSV table DSL, which has had a
few new functions added.  It has been designed to work cleanly with
Clojure's thread-first macro =->= though we might want to extend this
at some point to make it more monadic.

Note this DSL is not tied to the concrete implementation of CSV files,
but instead can operate on any arbitrary sequence of vectors.  Meaning
we only need to write =parse-shape-file= to generate a (lazy) sequence
of vectors and it will also work.

``` clojure
 ;; ^--- let bindings
        (-> (parse-csv "./test-data/glasgow-life-facilities.csv")
            (drop-rows 1)
            (swap {3 4})
            (mapc [uriify-facility _ parse-attendance parse-year convert-month address-line city post-code url])
            (derive-column uriify-pcode 7)
            (fuse date-time 3 4)
            (derive-column prefix-monthly-attendance 3)
            (derive-column slugify-facility 1)
            (fuse str 9 10)
            (derive-column prefix-facility 9))
```

You can read this DSL as applying the specified operations in order
(top to bottom) to a whole CSV file.  At each stage in the pipeline it
is as if a whole new CSV file is there, however underneath it is built
entirely out of lazy sequences; which means all of this is achieved in
only one iteration of the whole file; rather than the 10 iterations
you might typically expect.  To prove it consuming the whole sequence
takes 45ms:

``` clojure
grafter.rdf-examples> (time (dorun (make-life-facilities)))
"Elapsed time: 45.38 msecs"
```

But if we take just one item out of it, it only has to process 1 row
so it takes 2ms:

``` clojure
grafter.rdf-examples> (time (dorun (take 1 (make-life-facilities))))
"Elapsed time: 2.059 msecs"
```

Note the interesting thing here is that we can specify how much data
we want to consume outside of the core algorithm!  This allows us to
trivially use the same code to preview the first 50 rows.  The core
algorithm itself never implies how much work it will actually do.

Ok... So what is the code actually doing?

``` clojure
 ;; ^--- let bindings
        (-> (parse-csv "./test-data/glasgow-life-facilities.csv")
            (drop-rows 1)
            (swap {3 4})
            (mapc [uriify-facility _ parse-attendance parse-year convert-month address-line city post-code url])
            (derive-column uriify-pcode 7)
            (fuse date-time 3 4)
            (derive-column prefix-monthly-attendance 3)
            (derive-column slugify-facility 1)
            (fuse str 9 10)
            (derive-column prefix-facility 9))
```

First we do the boring stuff we load the file, and skip past
the first row because it's a header row.

Finally we start doing something interesting.  We swap the position of
columns 3 and 4.  Why?  Because our date function date-time expects to
receive the year first, followed by the month, so we can give our
users the necessary power to swap arguments by letting them do so in
the spreadsheet (DSL), rather than in code.

Next up we apply mapc to each row, where mapc takes each of the
functions we defined at the top in our function composition DSL and
applies them to specific columns in the spreadsheet.

These functions perform some initial input validation, and convert the
types from strings into more meaningful values [fn:1].

Next we use =derive-column= to apply a function to an existing column
and put the result in a new column at the end of the spreadsheet.
Here we take the postcode and convert it into a URI.

Currently the DSL has explicitly avoided supporting multiple parameter
function calls.  However it is clear that they are needed, so we need
a constrained way to allow the operation to occur.

=fuse= allows just this.  It takes an arbitrary number of column ids,
here the year and month, and applies each column to the supplied
function as an argument.  You could imagine a simple user interface
would easily allow users to select a function and the columns you want
to apply.  This DSL is ideally the only place we would allow the user
to use multi-argument functions.

Next we use derive-colum and fuse to build two more URI's, one for the
facility and the other for the monthly attendances.  Note that we show
the use of a user supplied javascript functions that we defined
earlier.

###Transform Templates : Triple Templates Revisited

I have developed Bill's triple templates idea, to support a
constrained data-based syntax using clojure vectors:

``` clojure
((graphify [facility-uri name attendance date street-address city postcode website postcode-uri
                    _ observation-uri]

                   (graph (base-graph "glasgow-life-facilities")
                          [facility-uri
                           [vcard:hasAddress [[rdf:a vcard:Address]
                                              [vcard:street-address street-address]
                                              [vcard:locality city]
                                              [vcard:country-name (rdfstr "Scotland")]
                                              [vcard:postal-code postcode-uri]
                                              [os:postcode postcode-uri]]]])

                   (graph (base-graph "glasgow-life-attendances")
                          [observation-uri
                           [(glasgow "refFacility") facility-uri]
                           [(glasgow "numAttendees") attendance]
                           [qb:dataSet "http://linked.glasgow.gov.uk/data/facility_attendance"]
                           [(sd "refPeriod") "http://reference.data.gov.uk/id/month/2013-09"]
                           [rdf:a qb:Observation]]))

         processed-rows)
        ))))
```

``` clojure
(defn urban-assets-ontology [ont-uri]
  (graph "http://linked.glasgow.gov.uk/graph/vocab/urban-assets/ontology"
         [ont-uri
          [rdf:a rdfs:Class]
          [rdfs:label (s "Urban Assets Ontology" :en)]]

         [(urban "Asset")
          [rdf:a rdfs:Class]
          [rdfs:label (s "Urban Asset")]
          [(rdfs "isDefinedBy") ont-uri]]

         [(glasgow "refAsset")
          [rdf:a (rdf "Property")]
          [rdf:a (qb "DimensionProperty")]
          [rdfs:label (s "Reference Asset" :en)]
          [(rdfs "range") (urban "Asset")]
          [(rdfs "isDefinedBy") ont-uri]]

         [(glasgow "numAssets")
          [rdf:a (rdf "Property")]
          [rdf:a (qb "MeasureProperty")]
          [rdfs:label (s "Number of Assets" :en)]
          [(rdfs "subPropertyOf") (sdmx-measure "obsValue")]
          [(rdfs "isDefinedBy") ont-uri]]))

(defn internal-ontology-metadata [ontology-uri date]
  (graph "http://linked.glasgow.gov.uk/graph/vocab/urban-assets/ontology/metadata"
         [ontology-uri
          [pmd:contactEmail "mailto:hello@glasgow.gov.uk"]
          [dcterms:title (s "Urban Assets Ontology" :en)]
          [dcterms:issued date]
          [dcterms:modified date]]))

(defn filter-triples [triples]
  (filter #(not (and (#{vcard:postal-code os:postcode} (pr/predicate %1))
                     (blank? (pr/object %1)))) triples))

(defn import-life-facilities [quads-seq]
  (let [now (java.util.Date.)]
    (->> quads-seq
         filter-triples
         (validate-triples (complement has-blank?))
         (load-triples my-repo))

    (->> (concat
          (dataset (str (base-uri "glasgow-life-facilities") "/data")
                   (str (base-graph "glasgow-life-facilities"))
                   now "Glasgow Life Facilities"
                   "Glasgow Life Facilities"
                   "List of Glasgow Life facilities"
                   "Sporting, cultural and social facilities in Glasgow."
                   "mailto:open@glasgow.gov.uk")

          (dataset (str (base-uri "glasgow-life-attendances"))
                   (str (base-graph "glasgow-life-attendances"))
                   now "Glasgow Life Attendances"
                   "Glasgow Life Attendances"
                   "Monthly Attendance figures for Glasgow Life Facilities"
                   "Monthly Attendances for Sporting, cultural and social facilities in Glasgow"
                   "mailto:open@glasgow.gov.uk")

          (urban-assets-ontology urban:ontology)
          (internal-ontology-metadata urban:ontology now))

         (load-triples my-repo))))
```

##Grafter API

Some namespace declarations to import the libraries:

``` clojure
(ns grafter.rdf-examples
  (:use [grafter.rdf]
        [grafter.rdf.sesame])
  (:require [grafter.rdf.protocols :as pr]))
```

Create and initialise a sesame native-store repository on disk:

``` clojure
(def my-repo (-> "./tmp/grafter-sesame-store" native-store repo))
```

Or use an in memory store:

``` clojure
(def my-memory-repo (-> "./tmp/grafter-sesame-store" memory-store repo))
```

Want to add some triples to your store?  The triplify function takes
a sequence of turtle style rdf subjects and expands them into a
lazy-seq of Triple records:

``` clojure
(triplify ["http://test.org/bob"
            ["http://is/a" "http://class/Person"]
            ["http://rdfs/label" (s "Bob Jones")]
            ["http://date-of-birth/" #inst "1980-01-02"]])

;; => (#grafter.rdf.protocols.Triple{:s "http://test.org/bob", :p "http://is/a", :o "http://class/Person"} #grafter.rdf.protocols.Triple{:s "http://test.org/bob", :p "http://rdfs/label", :o #<rdf$s$reify__1888 Bob Jones>} #grafter.rdf.protocols.Triple{:s "http://test.org/bob", :p "http://date-of-birth/", :o #inst "1980-01-02T00:00:00.000-00:00"})
```

*Note* how triplify assumes Strings in object position are URI's, if
you want a string wrap it in a call to =(s "String Value")=.  =s= also
takes an optional language tag or URI =(s "Bonjour!" "fr")=

Additionally =java.util.Date= is also expanded into xsd dateTime's,
which means you can use EDN =#inst= data literals too.

You can add Statements or sequences of statements to your store like
so with the =grafter.rdf.protocols/add= function:

``` clojure
(pr/add repo (expand-subject ["http://test.org/bob"
                                ["http://is/a" "http://class/Person"]
                                ["http://rdfs/label" (s "Bob Jones")]
                                ["http://date-of-birth/" #inst "1980-01-02"]]))
```

##Prefixer Ideas

One very obvious and simple idea I've had which I suspect might be a
good one (though it probably needs refining) is the =prefixer=
function.

=prefixer= takes a string as an argument and returns a function that
will generate the specified prefix e.g.

``` clojure
((prefixer "http://www.w3.org/1999/02/22-rdf-syntax-ns#") "Type") ;; => "http://www.w3.org/1999/02/22-rdf-syntax-ns#Type"
```

This allows you to do the following, which is syntactically quite
nice:

``` clojure
(def rdf (prefixer "http://www.w3.org/1999/02/22-rdf-syntax-ns#"))

(rdf "Type") ;; => "http://www.w3.org/1999/02/22-rdf-syntax-ns#Type"
```

I suspect that in practice prefixers or something like them will be
very useful, and are an ideal point for composing additional type
conversion pipelines etc...   e.g.

``` clojure
(def myprefixer (comp (prefixer "http://foobarbaz.com/museums/")
                       hyphenate-spaces
                       downcase
                       parse-name))
```

Note that you can also compose prefixers:

``` clojure
(def mydomain (prefixer "http://mydomain.com"))

(def life-facilities (comp
                      mydomain
                      (prefixer "/life-facilities")))

(def museum (comp life-facilities
                  (prefixer "/museums/")))

(museum "kelvin-grove") ;; => "http://mydomain.com/life-facilities/museums/kelvin-grove"
```

These simple prefixers have the benefit of only ever taking one
argument and converting it.

You can imagine multi-argument prefixers would be useful too, though
they may be harder to compose in a user interface.


##Misc

There is a demonstration of utilising GIS tools such as a shapefile
viewer which uses the [geotools API](http://geotools.org/).  This can be found in the
=grafter.gis.shape-viewer= namespace.

To run this run the following command:

``` clojure
    (show-shapefile (io/file "./test-data/dclg-enterprise-zones/National_EZ_WGS84.shp"))
```

## Footnotes

[fn:1] Types will likely become an issue, as it will be easy for users
to lose track of them.  I propose we look at Clojure's [core.typed](https://github.com/clojure/core.typed) as
an optional, dynamic type system that might be able to help provide
runtime introspection on type problems.  For example I suspect it
could be used to constrain available interface options on the basis of
the current type.
