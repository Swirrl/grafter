(ns ^{:added "0.12.1"}
 grafter-2.rdf4j.sparql
  "Functions for executing SPARQL queries with grafter RDF
  repositories, that support basic binding replacement etc."
  (:require [clojure.java.io :as io :refer [resource]]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [grafter-2.rdf4j.io :as rio]
            [grafter-2.rdf4j.repository :as repo
             :refer [->connection IPrepareQuery]]
            [grafter-2.rdf4j.sparql.path :as path])
  (:import java.util.regex.Pattern
           [org.eclipse.rdf4j.model Value]
           org.eclipse.rdf4j.rio.ntriples.NTriplesUtil
           org.eclipse.rdf4j.repository.RepositoryConnection
           [org.eclipse.rdf4j.query Query]))

(defn- get-clause-pattern [clause-name key]
  (cond
    (integer? key)
    (str "(?i)" clause-name "\\s+" key)

    (or (string? key) (keyword? key))
    (str "(?i)" clause-name "\\s+\\?" (name key))

    :else nil))

(defn ^:no-doc var-key-matcher [k]
  (cond (keyword? k)
        (let [k (str "\\?" (-> k
                               name
                               (str/replace "-" "_")))]
          k)
        ;; todo pad with whitespace matcher
        (sequential? k) (str "\\s*\\(\\s*" (str/join "\\s+" (map var-key-matcher k)) "\\s*\\)")
        :else (assert false "Error replacement not expected type")))

(defn ^:no-doc key->replacer [k]
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
    (NTriplesUtil/toNTriplesString (rio/->backend-type v))))

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

(defn- sequential-or-set? [c]
  (or (sequential? c) (set? c)))

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
       (filter (comp sequential-or-set? second))
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

(defn- path-binding? [[_ v]] (satisfies? path/PathString v))

(defn- rewrite-property-path-bindings [sparql-query bindings]
  (->> bindings
       (filter path-binding?)
       (reduce (fn [q [key path]]
                 (str/replace q
                              (re-pattern (var-key-matcher key))
                              (path/string-value path)))
               sparql-query)))

(defn- pre-process-query [sparql-query bindings]
  (-> sparql-query
      (strip-comments)
      (rewrite-limit-and-offset-clauses bindings)
      (rewrite-values-clauses bindings)
      (rewrite-property-path-bindings bindings)))


(s/def ::reasoning? boolean?)
(s/def ::query-opts (s/keys :req-un [::reasoning?]))

(s/def ::repo (partial satisfies? IPrepareQuery))

(s/def ::bound-value any?)

(s/def ::binding-name (s/and keyword?
                             ;; only the keyword slug need conform
                             ;; to the regex as we ignore ns
                             #(re-matches #"[a-z,A-Z]{1}(\p{Alnum}|-|_)*" (name %))))

(s/def ::simple-binding (s/tuple ::binding-name any?))

(s/def ::same-key-and-value-arity (fn [[k v]]
                                    (let [arity (count k)]
                                      (every? #(= arity (count %))
                                              v))))

(s/def ::values-binding-pair (s/tuple
                              sequential?
                              (s/coll-of ::bound-value)))

(s/def ::values-tuple-binding (s/and ::values-binding-pair
                                     ::same-key-and-value-arity))

;; bindings are given as a map of keys (binding names) to values.
;;
;; A simple binding is for the case in a SPARQL query where we want to
;; replace a single variable e.g. `?s` with a single value e.g. a URI.
;;
;; We might do that like so {:s (URI. "http://the/uri")}
;;
;; The second case is for binding to VALUES clauses where there may be
;; multiple bindings projected into the query.
;;
;; e.g. VALUES (?s ?p) { (:some-subject rdfs:label) (:some-other-subject skos:notation) }
;;
;; This is the :values-tuple-binding case, where we spec that the
;; binding key is a tuple like [:s :p] and the value is a sequence of
;; 0 or more tuples with the same arity as the projection.
;;
;; Though these are given as a map we spec them as a coll-of tuples,
;; so we can constrain that the key arity matches the value arity,
;; within a collection of values.
(s/def ::bindings (s/coll-of
                   (s/or :simple-binding ::simple-binding
                         :values-tuple-binding ::values-tuple-binding)))

(s/def ::query-args
  (s/cat :opts (s/? ::query-opts) :bind (s/? ::bindings) :repo (s/? ::repo)))

(defn ensure-sparql-file [sparql-file]
  (if (io/resource sparql-file)
    sparql-file
    (throw (ex-info "Could not find sparql file on resource path"
                    {:error :resource-file-not-found
                     :resource-path sparql-file}))))

(defmulti ^:private -query
  (fn [sparql-file & args]
    (let [{:keys [opts bind repo] :as conformed} (s/conform ::query-args args)]
      (if (s/invalid? conformed)
        conformed
        (cond-> [:sparql-file]
          opts (conj :opts)
          bind (conj :bind)
          repo (conj :repo))))))

(defmethod -query [:sparql-file]
  [sparql-file]
  (partial -query (ensure-sparql-file sparql-file)))

(defmethod -query [:sparql-file :opts]
  [sparql-file opts]
  (partial -query (ensure-sparql-file sparql-file) opts))

(defmethod -query [:sparql-file :repo]
  [sparql-file repo]
  (-query sparql-file {:reasoning? false} {} repo))

(defmethod -query [:sparql-file :bind :repo]
  [sparql-file opts bindings]
  (partial -query (ensure-sparql-file sparql-file) opts bindings))

(defmethod -query [:sparql-file :opts :repo]
  [sparql-file opts repo]
  (-query sparql-file opts {} repo))

(defmethod -query [:sparql-file :bind :repo]
  [sparql-file bindings repo]
  (-query sparql-file {:reasoning? false} bindings repo))

(defmethod -query [:sparql-file :opts :bind :repo]
  [sparql-file {:keys [reasoning?] :as opts} bindings repo]
  (let [sparql-query (slurp (resource sparql-file))
        pre-processed-qry (pre-process-query sparql-query bindings)
        prepped-query (repo/prepare-query repo pre-processed-qry nil opts)]
    (reduce (fn [^Query pq [unbound-var val]]
              (when-not (or (sequential? val) (set? val))
                (if (and (some? val) (satisfies? rio/IRDF4jConverter val))
                  (.setBinding pq (name unbound-var) (rio/->backend-type val))
                  (throw (ex-info (str "Could not coerce nil value into SPARQL binding for variable " unbound-var)
                                  {:variable unbound-var :bindings bindings :sparql-query sparql-query}))))
              pq)
            prepped-query
            (->> (dissoc bindings ::limits ::offsets)
                 (remove path-binding?)))
    prepped-query))

(defmethod -query ::s/invalid [sparql-file & args]
  (throw
   (ex-info
    (format "Arguments did not conform to spec %s\n%s"
            ::query-args
            (s/explain-str ::query-args args))
    {:type :illegal-argument-exception
     :spec (s/explain-data ::query-args args)})))

(defn query
  "Takes a string reference to a `sparql-file` on the resource path and
  optionally a map of bindings that should map SPARQL variables from your query
  to concrete values, allowing you to restrict and customise your query.

  The `opts` map is optional. Options include:

  - `:reasoning?` `true|false` whether or not reasoning/inference should be used
  in the query. DEFAULT: `false`

  The `bindings` map is optional, and if it's not provided then the query in the
  file is run as is.

  Additionally, if your sparql query specifies a LIMIT or OFFSET the bindings
  map supports the special keys ::limits and ::offsets.  Which should be maps
  binding identifiable limits/offsets from your query to new values.

  VALUES clause bindings are supported like normal ?var bindings when there is
  just one VALUES binding.  When there are more than one, you should provide a
  vector containing the component var names as the key in the map, with a
  sequence of sequences as the values themselves.  e.g. to override a clause
  like this:

  VALUES ?a ?b { (1 2) (3 4) }

  You would provide a map that looked like this:

  {[:a :b] [[1 1] [2 2] [3 3]]}

  nil's inside the VALUES row's themselves will raise an error.

  The clojure keyword :grafter-2.rdf.sparql/undef can be used to represent a
  SPARQL UNDEF, in the bound VALUES data.

  The final argument `repo` should be the repository to query.

  If only one argument referencing a resource path to a SPARQL query then a
  partially applied function is returned. e.g.

  (def spog (query \"grafter/rdf/sparql/select-spog.sparql\"))

  (spog r) ;; ... triples ...

  (spog r {:s [(URI. \"http://s1\") (URI. \"http://s2\")]}) ;; triples for VALUES clause subjects s.

  (spog r {:s (java.net.URI. \"http://example.org/data/a-triple\")}) ;; triples for given subject s.
  "
 {:arglists '([sparql-file]
              [sparql-file opts]
              [sparql-file repo]
              [sparql-file opts repo]
              [sparql-file bindings repo]
              [sparql-file opts bindings repo])}
  ([sparql-file & args]
   (let [q (apply -query sparql-file args)]
     (if (fn? q)
       (comp repo/evaluate q)
       (repo/evaluate q)))))

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
