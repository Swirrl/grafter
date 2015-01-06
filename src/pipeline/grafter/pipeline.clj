(ns grafter.pipeline
  (:refer-clojure :exclude [ns-name])
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as string]
            [grafter.common :refer [build-defgraft-docstring]])
  (:import [net.sf.corn.cps CPScanner ResourceFilter]
           [java.io InputStreamReader PushbackReader]))

(defn find-clj-classpath-resources []
  (let [resource-filter (doto (ResourceFilter.) (.resourceName "*.clj"))
        args (into-array ResourceFilter [resource-filter])]
    (CPScanner/scanResources args)))

(defn try-read [reader]
  (binding [*read-eval* false]
    (try
      (read reader false ::eof)
      (catch Exception e
        e))))

(defn forms
  "Given a Reader return a lazy sequence of its forms."
  [reader]
  (let [form (try-read reader)]
    (if-not (= ::eof form)
      (lazy-cat
       [form]
       (forms reader))
      (.close reader))))

(defn ns? [form]
  (when (seq? form)
    (= 'ns (first form))))

(defn pipe? [form]
  (when (seq? form)
    (= 'defpipe (first form))))

(defn graft? [form]
  (when (seq? form)
    (= 'defgraft (first form))))

(defn ns-name [nsform]
  (second nsform))

(defn pipeline-name [pipeform]
  (second pipeform))

;; type can be :pipe or :graft
(defrecord Pipeline [namespace name args doc meta body type])

(defn fully-qualified-name [pipeline]
  (when pipeline
    (symbol (str (:namespace pipeline)) (str (:name pipeline)))))

(defn- valid-decl? [decl msg]
  (if (seq decl)
    true
    (throw (IllegalArgumentException. msg))))

(defn pipe-form->Pipeline [ns form]
  (let [msg (str "Invalid grafter pipeline definition: " form)
        name (pipeline-name form)
        ;; NOTE as defpipeline mirrors defn this code uses the same
        ;; parsing algorithm as defn (as found in clojure.core) for
        ;; parsing function definitions
        pipdecl (drop 2 form)
        m (if (and (valid-decl? pipdecl msg)
                   (string? (first pipdecl)))
            {:doc (first pipdecl)}
            {})
        pipdecl (if (and (valid-decl? pipdecl msg) (string? (first pipdecl)))
                  (next pipdecl)
                  pipdecl)
        m (if (map? (first pipdecl))
            (conj m (first pipdecl))
            m)
        pipdecl (if (and (valid-decl? pipdecl msg) (map? (first pipdecl)))
                  (next pipdecl)
                  pipdecl)
        pipdecl (if (and (valid-decl? pipdecl msg) (vector? (first pipdecl)))
                  (list pipdecl)
                  pipdecl)
        m (if (and (valid-decl? pipdecl msg) (map? (last pipdecl)))
            (conj m (last pipdecl))
            m)

        args (if (and (valid-decl? pipdecl msg) (map? pipdecl))
               (butlast pipdecl)
               (ffirst pipdecl))

        body (->> (first pipdecl) (drop 1))]

    (when (or (not (symbol? name)) (not (vector? args)))
      (throw (IllegalArgumentException. msg)))

    (->Pipeline ns
                (pipeline-name form)
                args
                (:doc m)
                m
                body
                :pipe)))

(defn graft-form->Pipeline
  ([ns form] (graft-form->Pipeline ns form nil))
  ([ns form prevpipe]
   (cond
     (= 4 (count form)) (let [[_ name pipe graphfn] form]
                          (->Pipeline ns name
                                      (:args prevpipe)
                                      (build-defgraft-docstring pipe graphfn)
                                      nil
                                      `(comp ~graphfn ~pipe)
                                      :graft))
     (= 5 (count form)) (let [[_ name docstring pipe graphfn] form]
                          (->Pipeline ns name
                                      (:args prevpipe)
                                      docstring
                                      nil
                                      `(comp ~graphfn ~pipe)
                                      :graft))
     :else (throw (IllegalArgumentException. "Invalid graft form")))))

(defn find-pipelines
  ([forms]
   (find-pipelines forms nil))

  ([[form & forms] ns]
   (when form
     (cond
      (ns? form) (lazy-seq (find-pipelines forms (ns-name form)))
      (pipe? form) (try
                     (let [pipe (pipe-form->Pipeline ns form)]
                       (lazy-seq (cons pipe
                                       (find-pipelines forms ns))))
                     (catch Exception e
                       (lazy-seq (cons e (find-pipelines forms ns)))))
      (graft? form) (try
                      (let [graft (graft-form->Pipeline ns form)]
                        (lazy-seq (cons graft
                                        (find-pipelines forms ns))))
                      (catch Exception e
                        (lazy-seq (cons e (find-pipelines forms ns)))))

      (instance? Exception form) (lazy-seq (cons form (find-pipelines forms ns)))

      :else (lazy-seq (find-pipelines forms ns))))))

(defn inputstream->pushback-reader [is]
  (let [rs (io/input-stream is)]
    (PushbackReader. (InputStreamReader. rs))))

(defn find-resource-pipelines [url]
  (try
    (with-open [reader (inputstream->pushback-reader url)]
      (doall (find-pipelines (forms reader))))
    (catch java.io.FileNotFoundException e
      nil)))

(defmulti apply-pipeline
  "Takes a string representing a fully qualified function and reloads
  and requires its namespace before applying it to the supplied arguments.

  If given a function directly it will just apply it to the arguments
  as normal."
  (fn [pipeline inputs] (class pipeline)))

(defmethod apply-pipeline String [pipeline inputs]
  (let [fsym (symbol pipeline)
        ns (symbol (namespace fsym))]
    (require ns :reload-all)
    (let [f (ns-resolve ns fsym)]
      (if f
        (apply-pipeline f inputs)
        (throw (ex-info (str "Could not find pipeline " pipeline) {:error :could-not-find-pipeline :pipeline pipeline}))))))

(defmethod apply-pipeline clojure.lang.IFn [pipeline inputs]
  (apply pipeline inputs))

(comment
  (->> (find-clj-classpath-resources)
       (mapcat find-resource-pipelines)
       flatten
       (filter #(instance? grafter.pipeline.Pipeline %))))
