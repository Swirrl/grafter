(ns grafter.rdf.sparql
  (:require [grafter.rdf :refer [statements]]
            [grafter.rdf.repository :refer [repo sparql-repo ->connection]]
            [grafter.rdf.repository :as repo]
            [grafter.rdf.io :refer [->sesame-rdf-type] :as rio]
            [clojure.java.io :refer [resource]]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import [org.openrdf.rio.ntriples NTriplesUtil]
           [java.util.regex Pattern]))

(defn- get-clause-pattern [clause-name key]
  (cond
    (integer? key)
    (str "(?i)" clause-name "\\s+" key)

    (or (string? key) (keyword? key))
    (str "(?i)" clause-name "\\s+\\?" (name key))

    :else nil))

(defn var-key-matcher [k]
  (cond (keyword? k)
        (let [k (str "\\?" (-> k
                               name
                               (str/replace "-" "_")))]
          k)
        ;; todo pad with whitespace matcher
        (sequential? k) (str "\\s*\\(\\s*" (str/join "\\s+" (map var-key-matcher k)) "\\s*\\)")
        :else (assert false "Error replacement not expected type")))

(defn key->replacer [k]
  (let [whitespace-pat "\\s+"
        values-pat "(?i)values"
        var-pat (var-key-matcher k)
        body-mat #"\{(.*?)\}"]

    (Pattern/compile (str "(" values-pat whitespace-pat var-pat whitespace-pat "\\{).*?(\\})")
                     ;; make . match newlines
                     Pattern/DOTALL)))

(defn- serialise-val [v]
  (if (= ::undef v)
    "UNDEF"
    (NTriplesUtil/toNTriplesString (rio/->sesame-rdf-type v))))

(defn- ->sparql-str [k v]
  (cond
    (and (sequential? k) (sequential? v)
         (= (count k) (count v)))
    (str "(" (str/join " " (map serialise-val v)) ")")

    (and (not (sequential? k)) (not (sequential? v)))
    (serialise-val v)

    :else
    (assert false
            (str "VALUES clause keys & vals don't match up.  Key: " k " Val " v))))

(defn- rewrite-values-clauses* [q [k vals :as clause]]
  (let [regex (key->replacer k)
        values-block (str/join " " (map (partial ->sparql-str k) vals))]
    (str/replace q regex (str "$1 " values-block  " $2"))))

(defn- rewrite-values-clauses [q bindings]
  (->> bindings
       (map (fn [[k v]]
              (cond
                (= ::undef v)
                [k [v]]

                (nil? v)
                (throw
                 (ex-info (str "nil value SPARQL binding found for key " k
                               ". Consider explicitly binding value as ::sparql/undef")
                          {:bindings bindings
                           :sparql-query q
                           :error :nil-sparql-binding}))
                :else
                [k v])))
       (filter (comp sequential? second))
       (into {})
       (reduce rewrite-values-clauses* q)))

(defn- rewrite-clauses
  "Rewrites each instance of CLAUSE (literal | ?varname) with CLAUSE
  value with the given mappings."
  [sparql-query clause-name mappings]
  (reduce (fn [memo [key val]]
            (if-let [pattern (get-clause-pattern clause-name key)]
              (str/replace memo
                           (re-pattern pattern)
                           (str clause-name " " val))
              memo))
          sparql-query
          mappings))

(defn- rewrite-limit-and-offset-clauses
  "Replaces limit and offset clauses with values supplied as maps
  against matching SPARQL ?variable names or a limit integer"
  [query-str bindings]
  (-> query-str
      (rewrite-clauses "LIMIT" (::limits bindings))
      (rewrite-clauses "OFFSET" (::offsets bindings))))

(defn- strip-comments
  "Strip comments from a SPARQL query string"
  [query-str]
  (-> (str/replace query-str
                   #"(\s+#\s*[^\n]+)|(^#\s*[^\n]+)"
                   "")
      (str/trim)))

(defn- pre-process-query [sparql-query bindings]
  (-> sparql-query
      (strip-comments)
      (rewrite-limit-and-offset-clauses bindings)
      (rewrite-values-clauses bindings)))

(defn query
  "Takes a string reference to a sparql-file on the resource path and
  optionally a map of bindings that should map SPARQL variables from
  your query to concrete values, allowing you to restrict and
  customise your query.

  The bindings map is optional, and if it's not provided then the
  query in the file is run as is.

  Additionally, if your sparql query specifies a LIMIT or OFFSET the
  bindings map supports the special keys ::limits and ::offsets.
  Which should be maps binding identifiable limits/offsets from your
  query to new values.

  VALUES clause bindings are supported like normal ?var bindings when
  there is just one VALUES binding.  When there are more than one, you
  should provide a vector containing the component var names as the
  key in the map, with a sequence of sequences as the values
  themselves.  e.g. to override a clause like this:

  VALUES ?a ?b { (1 2) (3 4) }

  You would provide a map that looked like this:

  {[:a :b] [[1 1] [2 2] [3 3]]}

  nil's inside the VALUES row's themselves will raise an error.  

  The clojure keyword :grafter.rdf.sparql/undef can be used to
  represent a SPARQL UNDEF, in the bound VALUES data.

  The final argument `repo` should be the repository to query.

  If only one argument referencing a resource path to a SPARQL query
  then a partially applied function is returned. e.g.

  (def spog (query \"grafter/rdf/sparql/select-spog.sparql\"))

  (spog r) ;; ... triples ...

  (spog r {:s [(URI. \"http://s1\") (URI. \"http://s2\")]}) ;; triples for VALUES clause subjects s.

  (spog r {:s (java.net.URI. \"http://example.org/data/a-triple\")}) ;; triples for given subject s.
  "
  ([sparql-file]
   (if (io/resource sparql-file)
     (partial query sparql-file)
     (throw (ex-info "Could not find sparql file on resource path" {:error :resource-file-not-found
                                                                    :resource-path sparql-file}))))
  ([sparql-file repo]
   (query sparql-file {} repo))
  ([sparql-file bindings repo]
   (let [sparql-query (slurp (resource sparql-file))
         pre-processed-qry (pre-process-query sparql-query bindings)
         prepped-query (repo/prepare-query repo pre-processed-qry)]
     (reduce (fn [pq [unbound-var val]]
               (when-not (or (sequential? val) (set? val))
                 (if (and val (satisfies? rio/ISesameRDFConverter val))
                   (.setBinding pq (name unbound-var) (->sesame-rdf-type val))
                   (throw (ex-info (str "Could not coerce nil value into SPARQL binding for variable " unbound-var)
                                   {:variable unbound-var :bindings bindings :sparql-query sparql-query}))))
               pq)
             prepped-query
             (dissoc bindings ::limits ::offsets))
     (repo/evaluate prepped-query))))

(comment
  (def r (repo/resource-repo "grafter/rdf/sparql/sparql-data.trig"))

  (query "grafter/rdf/sparql/select-spog-pre-processed.sparql" {:p (java.net.URI. "http://www.w3.org/1999/02/22-rdf-syntax-ns#type")} (->connection r))

  (query "grafter/rdf/sparql/select-spog.sparql" {:p (java.net.URI. "http://www.w3.org/1999/02/22-rdf-syntax-ns#type")} (->connection r))

  (query "grafter/rdf/sparql/select-spog-pre-processed.sparql" r)

  ;; partial application

  (def spog (query "grafter/rdf/sparql/select-spog.sparql"))

  (spog r)

  (def pog (partial spog {:s (java.net.URI. "http://example.org/data/a-triple")}))

  (pog r)

  )
