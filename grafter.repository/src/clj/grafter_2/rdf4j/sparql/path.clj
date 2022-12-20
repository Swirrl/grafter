(ns grafter-2.rdf4j.sparql.path
  "An implementation of SPARQL Property Paths[1] for `grafter`/`rdf4j`.

  [1] https://www.w3.org/TR/sparql11-query/#propertypaths

  Syntax Form | Property Path Expression Name | Matches
  =====================================================
  iri         | PredicatePath      | An IRI. A path of length one.
  ^elt        | InversePath        | Inverse path (object to subject).
  elt1 / elt2 | SequencePath       | A sequence path of elt1 followed by elt2.
  elt1 | elt2 | AlternativePath    | A alternative path of elt1 or elt2 (all possibilities are tried).
  elt*        | ZeroOrMorePath     | A path that connects the subject and object of the path by zero or more matches of elt.
  elt+        | OneOrMorePath      | A path that connects the subject and object of the path by one or more matches of elt.
  elt?        | ZeroOrOnePath      | A path that connects the subject and object of the path by zero or one matches of elt.
  !expression | NegatedPropertySet | Negated property set. An IRI which is not one of `expression`. !iri is short for !(iri).
  (elt)       |                    | A group path elt, brackets control precedence.A zero occurrence of a path element always matches.

The order of IRIs, and reverse IRIs, in a negated property set is not significant and they can occur in a mixed order.

The precedence of the syntax forms is, from highest to lowest:

  * IRI, prefixed names
  * Negated property sets
  * Groups
  * Unary operators *, ? and +
  * Unary ^ inverse links
  * Binary operator /
  * Binary operator |

Precedence is left-to-right within groups."
  (:refer-clojure :exclude [/ * + ? -])
  (:require [clojure.spec.alpha :as s]))

(defprotocol PathString
  ;; NOTE: Please do not extend this protocol to types that are not representing
  ;; the AST of this Property Path DSL. There could be trouble with substitution
  ;; if it was satisfied for, E.G., URI
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

(defn -
  "Translates to ^x : InversePath"
  [x]
  (Group. (prefix "^" x)))

(defn /
  "A sequence path of x, followed by y, followed by zs"
  [x y & zs]
  (Group. (binop "/" (concat [x y] zs))))

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

(defn !
  "Translates to !x : NegatedPropertySet"
  [x]
  (Group. (prefix "!" x)))


(defn- sym-prefix? [prefix sym]
  (.startsWith (name sym) (name prefix)))

(defn- sym-suffix? [suffix sym]
  (.endsWith (name sym) (name suffix)))

(defn- no-suffix? [sym]
  (not (or (sym-suffix? '* sym)
           (sym-suffix? '+ sym)
           (sym-suffix? '? sym))))

(defn- prefix? [prefix]
  (s/and symbol? (partial sym-prefix? prefix)))

(defn- suffix? [suffix]
  (s/and symbol? (partial sym-suffix? suffix)))

(s/def ::expr1
  (s/or :presuf (s/and (prefix? '-)
                       (s/or :sym* (suffix? '*)
                             :sym+ (suffix? '+)
                             :sym? (suffix? '?)))
        :!sym   (s/and (prefix? '!) no-suffix?)
        :sym*   (suffix? '*)
        :sym+   (suffix? '+)
        :sym?   (suffix? '?)
        :-sym   (prefix? '-)
        :simple (s/or :uri uri?
                      :sym (s/and symbol? (complement '#{! / | * + ? -})))
        :group  (s/and seq? ::expr)
        :sexp  (s/and seq? #(not (s/valid? ::expr %)))))

(s/def ::expr-presuf
  (s/alt :!expr  (s/cat :! '#{!} :x ::expr1)
         :expr* (s/cat :x ::expr1 :* '#{*})
         :expr+ (s/cat :x ::expr1 :* '#{+})
         :expr? (s/cat :x ::expr1 :* '#{?})
         :-expr (s/cat :- '#{-} :x ::expr1 :suffix (s/? '#{* + ?}))))

(s/def ::expr-part
  (s/alt :expr ::expr-presuf
         :expr1 ::expr1))

(s/def ::expr-seq
  (s/alt :sequence (s/cat :a ::expr-part :/ #{'/} :b ::expr)
         :altseq   (s/cat :a ::expr-part :/ #{'|} :b ::expr)))

(s/def ::expr
  (s/alt :expr1 ::expr-part
         :expr  ::expr-seq))

(defn fix-sym [name]
  (symbol "grafter-2.rdf4j.sparql.path" name))

(defn- split-prefix [x]
  (let [name (name x)]
    [(symbol (namespace x) (subs name 1))
     (fix-sym (subs name 0 1))]))

(defn- split-suffix [x]
  (let [name (name x)
        c    (dec (count name))]
    [(symbol (namespace x) (subs name 0 c))
     (fix-sym (subs name c))]))

(defn- split-presuf [x]
  (let [name (name x)
        c    (dec (count name))]
    [(symbol (namespace x) (subs name 1 c))
     (fix-sym (subs name c))
     (fix-sym (subs name 0 1))]))

(defn- throw-ambiguous [& bindings]
  (throw
   (ex-info (str "Ambiguous syntax, bindings in scope: " bindings)
            {:type ::ambiguous-syntax :bindings bindings})))

(defn- unfixed-expr [env sym split-fns]
  (letfn [(binding? [x] (or (contains? env x) (boolean (resolve x))))
          (apply-fix-fns [[x & fns]] (reduce (fn [x f] (list f x)) x fns))]
    (let [syms  (cons [sym] ((apply juxt split-fns) sym))
          bound (filter (comp binding? first) syms)
          c     (count bound)]
      (cond (> c 1) (apply throw-ambiguous (map first bound))
            (= c 1) (apply-fix-fns (first bound))
            :else   (apply-fix-fns (last syms))))))

(defn- parse-path-expr [env x]
  (if (s/invalid? x)
    (throw (ex-info "Property Path syntax invalid"
                    {:type ::property-path-syntax-invalid}))
    (let [[t e] x]
      (case t
        :sym*     (unfixed-expr env e [split-suffix])
        :sym+     (unfixed-expr env e [split-suffix])
        :sym?     (unfixed-expr env e [split-suffix])
        :-sym     (unfixed-expr env e [split-prefix])
        :!sym     (unfixed-expr env e [split-prefix])
        :presuf   (unfixed-expr env (val e) [split-prefix split-suffix split-presuf])
        :expr*    (list `* (parse-path-expr env (:x e)))
        :expr+    (list `+ (parse-path-expr env (:x e)))
        :expr?    (list `? (parse-path-expr env (:x e)))
        :-expr    (let [expr (parse-path-expr env (:x e))]
                    (list `- (or (some-> e :suffix name fix-sym (list expr)) expr)))
        :!expr    (list `! (parse-path-expr env (:x e)))
        :expr     (parse-path-expr env e)
        :expr1    (parse-path-expr env e)
        :sequence (list `/ (parse-path-expr env (:a e)) (parse-path-expr env (:b e)))
        :altseq   (list `| (parse-path-expr env (:a e)) (parse-path-expr env (:b e)))
        :simple   (parse-path-expr env e)
        :uri      e
        :sym      e
        :group    (parse-path-expr env e)
        :sexp     e))))

(defmacro path
  "Build a path with syntax similar to SPARQL property path syntax.

  SPARQL Form | Clojure Form         | Expression Name
  ====================================================
  iri         | iri                  | PredicatePath
  ^elt        | -elt - or - (- elt)  | InversePath
  elt1 / elt2 | elt1 / elt2          | SequencePath
  elt1 | elt2 | elt1 | elt2          | AlternativePath
  elt*        | elt* - or - (elt *)  | ZeroOrMorePath
  elt+        | elt+ - or - (elt +)  | OneOrMorePath
  elt?        | elt? - or - (elt ?)  | ZeroOrOnePath
  !expression | !expression          | NegatedPropertySet
  (elt)       | (elt)                | Group

  E.G.,

  (path -a / b* / !c | d? | !(java.net.URI. (str \"http://www.grafter.org/example#\" p)))

  Where bindings a, b, c, d are URIs, and binding p is a String."
  [& path]
  (parse-path-expr &env (s/conform ::expr path)))
