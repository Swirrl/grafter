(ns grafter.rdf.preview
  "Tool support for rendering previews of grafter.tabular graph-fn templates
  with values from datasets."
  (:require [rewrite-clj.zip :as z]
            [rewrite-clj.node :as n]
            [rewrite-clj.parser :as p]
            [rewrite-clj.zip.walk :as walk]))

(defn- str->sexpr [x]
  (-> x pr-str  p/parse-string-all n/sexpr))

(defn- str->clj-zipper [s]
  (z/edn (p/parse-string-all (pr-str s))))

(defn- substitution-map [dataset bindings row-data]
  (merge (select-keys row-data (map keyword (:keys bindings)))
         (select-keys row-data (map str (:strs bindings)))))

(defmulti templatable? class)

(defmethod templatable? nil [v] true)

(defmethod templatable? Object [v] false)

(defmethod templatable? Number [v] true)

(defmethod templatable? String [v] true)

(defmethod templatable? clojure.lang.Keyword [v] true)

(defn- bind-constants [ns form]
  (z/root (walk/prewalk form (fn [n]
                            (let [s (z/sexpr n)]
                              (if (and (symbol? s)
                                       (ns-resolve ns s))
                                (when-let [varr (get (ns-map ns) s)]
                                  (when (templatable? @varr)
                                    (z/replace n @varr)))
                                n))))))

(defn- bind-row-data [subs body-zip]
  (z/root (walk/prewalk body-zip
                        (fn [n]
                          (let [val (z/sexpr n)]
                            (if (symbol? val)
                              (let [rep (or (get subs (str val))
                                            (get subs (keyword (str val))))]
                                (when rep
                                  (z/replace
                                   n rep)))
                              n))))))

(defn preview-graph
  "Takes a dataset a function built via grafter.tabular/graph-fn and a row
  number, and returns an EDN datastructure representing the template (body) of
  the graph-fn function with column variables substituted for data.

  Takes an optional final argument which can be :render-constants, if the user
  would also like to substitute symbols from within the graph-fn body with
  renderable constants found in referenced vars."
  ([dataset graphf row]
   (preview-graph dataset graphf row false))

  ([dataset graphf row render-constants?]
   (let [form-str (:grafter.tabular/template (meta graphf))
         form (str->sexpr form-str)
         bindings (-> (str->clj-zipper form-str) z/next z/next z/next z/sexpr)
         body-forms (drop 2 form)
         row-data (nth (:rows dataset) row nil)
         subs (substitution-map dataset bindings row-data)]
     {:bindings bindings :row row-data :template
      (->> body-forms
           (map (fn [body-form]
                  (let [body-zip (-> (z/edn (p/parse-string-all (pr-str body-form))))]
                    (let [replaced (bind-row-data subs body-zip)]
                      (read-string (n/string (if (= :render-constants render-constants?)
                                               (bind-constants (:grafter.tabular/defined-in-ns (meta graphf))
                                                               (z/edn replaced))
                                               replaced))))))))})))

(comment
  (require '(grafter [tabular :refer [graph-fn make-dataset]]))
  (require '(grafter.rdf [templater :refer [graph]]))

  (def my-template (graph-fn [{:strs [a b c] :keys [c]}]
                             (graph "http://foo.com/"
                                    [(first b)
                                     [b c]]
                                    [a
                                     [b c]
                                     [b c]
                                     [b c]])
                             (graph a [a [b c]]))))
