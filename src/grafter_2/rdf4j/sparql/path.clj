(ns grafter-2.rdf4j.sparql.path
  "A limited implementation of SPARQL Property Paths[1] for `grafter`/`rdf4j`.

  Limitations:

  Paths must start and end with a \"plain\" `uri`. Meaning, no prefix or suffix
  modifiers.

  This is due to the rdf4j implementation, and the hook used to shoehorn this
  in.

  Additionally, `group`s are not implemented for the same reason.

  Therefore, this implementation will not be able to translate complex paths, but
  simple paths, E.G., `x ^y{3} / z` will be possible.

[1] https://www.w3.org/TR/sparql11-property-paths/#path-language

Syntax Form | Matches
uri         | A URI or a prefixed name. A path of length one.
^elt        | Inverse path (object to subject).
(elt)       | A group path elt, brackets control precedence.
elt1 / elt2 | A sequence path of elt1, followed by elt2
elt1 ^ elt2 | Shorthand for elt1 / ^elt2, that is elt1 followed by the inverse of elt2.
elt1 | elt2 | A alternative path of elt1, or elt2 (all possibilities are tried).
elt*        | A path of zero or more occurrences of elt.
elt+        | A path of one or more occurrences of elt.
elt?        | A path of zero or one elt.
elt{n,m}    | A path between n and m occurrences of elt.
elt{n}      | Exactly n occurrences of elt. A fixed length path.
elt{n,}     | n or more occurrences of elt.
elt{,n}     | Between 0 and n occurrences of elt.

A zero occurrence of a path element always matches.

Precedence:

    URI, prefixed names
    Groups
    Unary operators *, ?, + and {} forms
    Unary ^ inverse links
    Binary operators / and ^
    Binary operator |

Precedence is left-to-right within groups."
  (:refer-clojure :exclude [/ * + ?])
  (:require [grafter-2.rdf4j.io :as rio]
            [clojure.string :as string]
            [clojure.spec.alpha :as s]))


(defprotocol PathString
  (flat [_])
  (string-value [_]))

(deftype Arg [x]
  PathString
  (flat [y] [y])
  (string-value [_] (str \< x \>))
  rio/IRDF4jConverter
  (rio/->backend-type [_]
    (rio/->backend-type x)))

(deftype Prefix [op x]
  PathString
  (flat [y] [y])
  (string-value [_] (str op (string-value x)))
  rio/IRDF4jConverter
  (rio/->backend-type [_]
    (throw (ex-info "Cannot convert Prefix to backend type"
                    {:type ::prefix-conversion-exception}))))

(deftype Suffix [op x]
  PathString
  (flat [y] [y])
  (string-value [_] (str (string-value x) op))
  rio/IRDF4jConverter
  (rio/->backend-type [_]
    (throw (ex-info "Cannot convert Suffix to backend type"
                    {:type ::suffix-conversion-exception}))))

(deftype BinOp [op x y]
  PathString
  (flat [_] (concat (flat x) (flat y)))
  (string-value [_] (str (string-value x) op (string-value y)))
  rio/IRDF4jConverter
  (rio/->backend-type [z]
    (let [flat (flat z)]
      (when (instance? Prefix (first flat))
        (throw (ex-info "Cannot start path with a prefix"
                        {:type ::prefix-path-start-exception})))
      (when (instance? Suffix (last flat))
        (throw (ex-info "Cannot end path with a suffix"
                        {:type ::prefix-path-start-exception}))))
    (let [s (string-value z)]
      (reify org.eclipse.rdf4j.model.IRI
        (stringValue [_]
          (subs s 1 (dec (count s))))))))

(defn- arg [x]
  (if (or (instance? Prefix x) (instance? Suffix x) (instance? BinOp x))
    x
    (Arg. x)))

(defn- binop [op [x & uris]]
  (if (seq uris)
    (BinOp. op (arg x) (binop op uris))
    (arg x)))

(defn- prefix [op x]
  (Prefix. op (arg x)))

(defn- suffix [op x]
  (Suffix. op (arg x)))

(defn /
  "A sequence path of x, followed by y, followed by zs"
  [x y & zs]
  (binop "/" (concat [x y] zs)))

(defn !
  "In arity [x]:
  Translates to ^x
  Inverse path (object to subject).

  In arity [x y & zs]:
  Shorthand for x / ^y / ^z1 ..., that is x followed by the inverse of y ..."
  ([x]
   (prefix "^" x))
  ([x y & zs]
   (binop "^" (concat [x y] zs))))

(defn |
  "A alternative path of x, or y, or zs (all possibilities are tried)."
  [x y & zs]
  (binop "|" (concat [x y] zs)))

(defn *
  "A path of zero or more occurrences of x"
  [x]
  (suffix "*" x))

(defn +
  "A path of one or more occurrences of x"
  [x]
  (suffix "+" x))

(defn ?
  "A path of zero or one x"
  [x]
  (suffix "?" x))

(defn n
  "m occurrences of x, where m is one of:
  * {0, n} - Zero to n occurrences
  * {n, n} - Exactly n occurrences
  * n      - Exactly n occurrences
  * {n, *} - n or more occurrences"
  [x m]
  (cond (map? m)
        (let [[n m] (first m)]
          (cond (= m '*)
                (suffix (format "{%s,}" n) x)
                (= m clojure.core/*)
                (suffix (format "{%s,}" n) x)
                (= m *)
                (suffix (format "{%s,}" n) x)
                (= n m)
                (suffix (format "{%s}" n) x)
                :else
                (suffix (format "{%s,%s}" n m) x)))
        (integer? m)
        (suffix (format "{%s}" m) x)))
