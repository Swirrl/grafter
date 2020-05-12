(ns grafter-2.rdf4j.sparql.path
  "An implementation of SPARQL Property Paths[1] for `grafter`/`rdf4j`.

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
  (string-value [_]))

(deftype Arg [x]
  PathString
  (string-value [_]
    (if (satisfies? PathString x)
      (string-value x)
      (str \< x \>))))

(deftype Group [x]
  PathString
  (string-value [_] (str \( (string-value x) \))))

(deftype Prefix [op x]
  PathString
  (string-value [_] (str op (string-value x))))

(deftype Suffix [op x]
  PathString
  (string-value [_] (str (string-value x) op)))

(deftype BinOp [op x y]
  PathString
  (string-value [_] (str (string-value x) op (string-value y))))

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
  (Group. (binop "/" (concat [x y] zs))))

(defn !
  "In arity [x]:
  Translates to ^x
  Inverse path (object to subject).

  In arity [x y & zs]:
  Shorthand for x / ^y / ^z1 ..., that is x followed by the inverse of y ..."
  ([x]
   (Group. (prefix "^" x)))
  ([x y & zs]
   (Group. (binop "^" (concat [x y] zs)))))

(defn |
  "A alternative path of x, or y, or zs (all possibilities are tried)."
  [x y & zs]
  (Group. (binop "|" (concat [x y] zs))))

(defn *
  "A path of zero or more occurrences of x"
  [x]
  (Group. (suffix "*" x)))

(defn +
  "A path of one or more occurrences of x"
  [x]
  (Group. (suffix "+" x)))

(defn ?
  "A path of zero or one x"
  [x]
  (Group. (suffix "?" x)))

(defn n
  "m occurrences of x, where m is one of:
  * {0, n} - Zero to n occurrences
  * {n, n} - Exactly n occurrences
  * n      - Exactly n occurrences
  * {n, *} - n or more occurrences"
  [x m]
  (Group.
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
         (suffix (format "{%s}" m) x))))

(defn- prefix? [prefix]
  (s/and symbol? (fn [x] (.startsWith (name x) (name prefix)))))

(defn- suffix? [suffix]
  (s/and symbol? (fn [x] (.endsWith (name x) (name suffix)))))

(s/def ::expr1
  (s/or :sym*   (suffix? '*)
        :sym+   (suffix? '+)
        :sym?   (suffix? '?)
        :!sym   (prefix? '!)
        :expr*  (s/cat :x ::expr1 :* '#{*})
        :expr+  (s/cat :x ::expr1 :* '#{+})
        :expr?  (s/cat :x ::expr1 :* '#{?})
        :!expr  (s/cat :! '#{!} :x ::expr1)
        :simple (s/or :uri uri?
                      :sym (s/and symbol? (complement '#{! / | * + ?})))
        :group  (s/and seq? ::expr)
        :n-m    (s/cat :x ::expr1 :n-m (s/map-of integer? integer?))
        :n-*    (s/cat :x ::expr1 :n-m (s/map-of integer? #{'*}))
        :n      (s/cat :x ::expr1 :n integer?)
        :sexp   (s/and seq? #(not (s/valid? ::expr %)))))

(s/def ::expr
  (s/alt :expr1    ::expr1
         :sequence (s/cat :a ::expr1 :/ #{'/} :b ::expr)
         :invseq   (s/cat :a ::expr1 :/ #{'!} :b ::expr)
         :altseq   (s/cat :a ::expr1 :/ #{'|} :b ::expr)))

(defn- parse-path-expr [x]
  (if (s/invalid? x)
    (throw (ex-info "Property Path syntax invalid"
                    {:type ::property-path-syntax-invalid}))
    (letfn [(del-suffix [x]
              (let [name (name x)]
                (symbol (namespace x) (subs name 0 (dec (count name))))))
            (del-prefix [x]
              (symbol (namespace x) (subs (name x) 1)))]
      (let [[t e] x]
        (case t
          :sym*     (list `* (del-suffix e))
          :sym+     (list `+ (del-suffix e))
          :sym?     (list `? (del-suffix e))
          :!sym     (list `! (del-prefix e))
          :expr*    (list `* (parse-path-expr (:x e)))
          :expr+    (list `+ (parse-path-expr (:x e)))
          :expr?    (list `? (parse-path-expr (:x e)))
          :!expr    (list `! (parse-path-expr (:x e)))
          :expr     (parse-path-expr e)
          :expr1    (parse-path-expr e)
          :sequence (list `/ (parse-path-expr (:a e)) (parse-path-expr (:b e)))
          :invseq   (list `! (parse-path-expr (:a e)) (parse-path-expr (:b e)))
          :altseq   (list `| (parse-path-expr (:a e)) (parse-path-expr (:b e)))
          :simple   (parse-path-expr e)
          :uri      e
          :sym      e
          :group    (parse-path-expr e)
          :n-m      (list `n (parse-path-expr (:x e)) (:n-m e))
          :n-*      (list `n (parse-path-expr (:x e)) (:n-m e))
          :n        (list `n (parse-path-expr (:x e)) (:n e))
          :sexp     e)))))

(defmacro path
  "Build a path with syntax similar to SPARQL property path syntax.

  SPARQL Form | Clojure Form
  uri         | uri
  ^elt        | !elt
  ^elt        | (! elt)
  (elt)       | (elt)
  elt1 / elt2 | elt1 / elt2
  elt1 ^ elt2 | elt1 ! elt2
  elt1 ^ elt2 | elt1 / !elt2 - NOTE: only this form works with Sail repo
  elt1 | elt2 | elt1 | elt2
  elt*        | elt* - or - (elt *)
  elt+        | elt+ - or - (elt +)
  elt?        | elt? - or - (elt ?)
  elt{n,m}    | (elt {n m})  - NOTE: n,m forms do not work with Sail repo
  elt{n}      | (elt n) or (elt {n n})
  elt{n,}     | (elt {n *})
  elt{,n}     | (elt {0 n})

  E.G.,

  (path !a / b ! (c* {3 *}) | (d 3) | (java.net.URI. (str \"http://www.grafter.org/example#\" p)))

  Where bindings a, b, c, d are URIs, and binding p is a String."
  [& path]
  (parse-path-expr (s/conform (s/or :expr ::expr :expr1 ::expr1) path)))
