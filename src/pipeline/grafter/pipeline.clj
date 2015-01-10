(ns grafter.pipeline
  {:no-doc true}
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

(defn fully-qualify-symbol [ns sym]
  (symbol (str ns) (str sym)))

(defn fully-qualified-name [pipeline]
  (when pipeline
    (fully-qualify-symbol (:namespace pipeline) (:name pipeline))))

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
  ([ns form] (graft-form->Pipeline ns form {}))
  ([ns form pipe-definitions]
   (cond
     (= 3 (count form)) (let [[_ name pipe] form]
                          (->Pipeline ns
                                      name
                                      (:args (pipe-definitions (fully-qualify-symbol ns pipe)))
                                      (build-defgraft-docstring pipe)
                                      nil
                                      pipe
                                      :graft))
     (= 4 (count form)) (let [[_ name pipe graphfn] form]
                          (->Pipeline ns name
                                      (:args (pipe-definitions (fully-qualify-symbol ns pipe)))
                                      (build-defgraft-docstring pipe graphfn)
                                      nil
                                      `(comp ~graphfn ~pipe)
                                      :graft))
     (<= 5 (count form)) (let [[_ name docstring-or-pipe pipe-or-graphfn graphfn-or-quadfn & quadfns] form
                               docstring (if (string? docstring-or-pipe)
                                           docstring-or-pipe
                                           (build-defgraft-docstring docstring-or-pipe pipe-or-graphfn))

                               [pipe form] (if (string? docstring-or-pipe)
                                             [pipe-or-graphfn
                                              (apply list pipe-or-graphfn graphfn-or-quadfn quadfns)]
                                             [docstring-or-pipe
                                              (apply list docstring-or-pipe pipe-or-graphfn graphfn-or-quadfn quadfns)])

                               comp-form (cons 'clojure.core/comp (reverse form))]

                           (->Pipeline ns name
                                       (:args (pipe-definitions (fully-qualify-symbol ns pipe)))
                                       docstring
                                       (:arglists (meta pipe))
                                       comp-form
                                       :graft))
     :else (throw (IllegalArgumentException. "Invalid graft form")))))

;; NOTE that defgrafts metadata is only properly resolved when
;; the defgraft is specified after a pipeline and when it's in the
;; same namespace as it.
;;
;; TODO: Change this to traverse namespaces by resolving them in the
;; same order as clojure, to ensure that we can
;; correctly unambiguously resolve args.  Should probably use clojure
;; tools.namespace.
(defn find-pipelines
  ([forms]
   (find-pipelines forms nil {}))


  ([[form & forms] ns prevpipes] ; prevpipes is type {sym -> Pipeline}
   (when form
     (cond
       (ns? form) (lazy-seq (find-pipelines forms (ns-name form) prevpipes))
       (pipe? form) (try
                      (let [pipe (pipe-form->Pipeline ns form)]
                        (lazy-seq (cons pipe
                                        (find-pipelines forms ns (assoc prevpipes
                                                                       (fully-qualified-name pipe)
                                                                       pipe)))))
                      (catch Exception e
                        (lazy-seq (cons e (find-pipelines forms ns prevpipes)))))
       (graft? form) (try
                       (let [graft (graft-form->Pipeline ns form prevpipes)]
                         (lazy-seq (cons graft
                                         (find-pipelines forms ns prevpipes))))
                       (catch Exception e
                         (lazy-seq (cons e (find-pipelines forms ns prevpipes)))))

       (instance? Exception form) (lazy-seq (cons form (find-pipelines forms ns prevpipes)))

       :else (lazy-seq (find-pipelines forms ns prevpipes))))))

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

(defn all-pipelines-on-classpath []
  "Returns all the pipelines found on the classpath."
  (for [url (find-clj-classpath-resources)
        pipeline (->> (find-resource-pipelines url)
                      (remove #(instance? Exception %)))]
    pipeline))

(defn- type? [type]
  (fn [pipeline]
    (= type (:type pipeline))))

(defn all-pipes-on-classpath []
  (filter (type? :pipe) (all-pipelines-on-classpath)))

(defn all-grafts-on-classpath []
  (filter (type? :graft) (all-pipelines-on-classpath)))

(comment
  (->> (find-clj-classpath-resources)
       (mapcat find-resource-pipelines)
       flatten
       (filter #(instance? grafter.pipeline.Pipeline %))))
