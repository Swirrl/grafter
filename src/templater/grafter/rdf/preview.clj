(ns grafter.rdf.preview
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

(defn preview-graph [dataset graphf row]
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
                   (read-string (n/string (z/root (walk/prewalk body-zip
                                                                (fn [n]
                                                                  (let [val (z/sexpr n)]
                                                                    (if (symbol? val)
                                                                      (let [rep (or (get subs (str val))
                                                                                    (get subs (keyword (str val))))]
                                                                        (when rep
                                                                          (z/replace n rep)))
                                                                      n)))))))))))}))

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
                             (graph a [a [b c]])))



  )
