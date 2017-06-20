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
  (NTriplesUtil/toNTriplesString (rio/->sesame-rdf-type v)))

(defn- ->sparql-str [k v]
  (cond
    (nil? v)
    "UNDEF"

    (and (sequential? k) (sequential? v)
         (= (count k) (count v)))
    (str "(" (str/join " " (map serialise-val v)) ")")

    (and (not (sequential? k)) (not (sequential? v)))
    (serialise-val v)

    :else
    (assert false (str "Key and value types don't match.  Key: " k " Val " v))))

(defn- rewrite-values-clauses* [q [k vals :as clause]]
  (let [regex (key->replacer k)
        values-block (str/join " " (map (partial ->sparql-str k) vals))]
    (str/replace q regex (str "$1 " values-block  " $2"))))

(defn- rewrite-values-clauses [q bindings]
  (let [values-bindings (->> bindings
                             (filter (comp sequential? second))
                             (into {}))]
    (reduce rewrite-values-clauses* q values-bindings)))

(defn- rewrite-clauses
  "Rewrites each instance of CLAUSE (literal | ?varname) with CLAUSE
  value with the given mappings."
  [sparl-query clause-name mappings]
  (reduce (fn [memo [key val]]
            (if-let [pattern (get-clause-pattern clause-name key)]
              (str/replace memo
                           (re-pattern pattern)
                           (str clause-name " " val))
              memo))
          sparl-query
          mappings))

(defn- rewrite-limit-and-offset-clauses
  "Replaces limit and offset clauses with values supplied as maps
  against matching SPARQL ?variable names or a limit integer"
  [query-str bindings]
  (-> query-str
      (rewrite-clauses "LIMIT" (::limits bindings))
      (rewrite-clauses "OFFSET" (::offsets bindings))))

(defn query
  "Takes a string reference to a sparql-file on the resource path and
  optionally a map of bindings that should map SPARQL variables from
  your query to concrete values, allowing you to restrict and
  customise your query.

  The bindings map is optional, and if it's not provided then the
  query in the file is run as is.

  Additionally if your sparql query specifies a LIMIT or OFFSET the
  bindings map supports the special keys ::limits and ::offstets.
  Which should be maps binding identifiable limits/offsets from your
  query to new values.

  The final argument should be the repository to query.

  If only one argument referencing a resource path to a SPARQL query
  then a partially applied function is returned.  e.g.

  (def spog (query \"grafter/rdf/sparql/select-spog.sparql\"))

  (spog r) ;; ... triples ...

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
         pre-processed-qry (-> sparql-query
                               (rewrite-limit-and-offset-clauses bindings)
                               (rewrite-values-clauses bindings))

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
