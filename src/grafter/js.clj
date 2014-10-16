(ns grafter.js
  {:no-doc true}
  (:require [clj-rhino :as js]))

(defn js-fn
  "Compile a javascript function and return a clojure function that
calls it."
  ([js-str]
     (js-fn js-str Integer/MAX_VALUE))
  ([js-str timeout]
     (let [scope (js/new-safe-scope)
           jsf (js/compile-function scope js-str)]
       (fn javascript-function [& args]
         (apply js/call-timeout (concat [scope jsf timeout] args) ))
       )))
