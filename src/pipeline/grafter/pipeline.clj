(ns grafter.pipeline
  (:refer-clojure :exclude [ns-name])
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as string])
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

(defn pipeline? [form]
  (when (seq? form)
    (= 'defpipeline (first form))))

(defn ns-name [nsform]
  (second nsform))

(defn pipeline-name [pipeform]
  (second pipeform))

(defrecord Pipeline [namespace name args doc meta body])

(defn fully-qualified-name [pipeline]
  (symbol (str (:namespace pipeline)) (str (:name pipeline))))

(defn- valid-decl? [decl msg]
  (if (seq decl)
    true
    (throw (IllegalArgumentException. msg))))

(defn form->Pipeline [ns form]
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
                body)))

(defn find-pipelines
  ([forms]
   (find-pipelines forms nil))

  ([[form & forms] ns]
   (when form
     (cond
      (ns? form) (find-pipelines forms (ns-name form))
      (pipeline? form) (try
                         (let [pipe (form->Pipeline ns form)]
                           (lazy-seq (cons pipe
                                           (find-pipelines forms ns))))
                         (catch Exception e
                           (cons e (find-pipelines forms ns))))
      (instance? Exception form) (cons form (find-pipelines forms ns))

      :else (find-pipelines forms ns)))))

(defn inputstream->pushback-reader [is]
  (let [rs (io/input-stream is)]
    (PushbackReader. (InputStreamReader. rs))))

(defn find-resource-pipelines [url]
  (with-open [reader (inputstream->pushback-reader url)]
    (find-pipelines (forms reader))))
