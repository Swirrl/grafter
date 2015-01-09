(ns grafter.common
  {:no-doc true})

;; A small shared library of miscellaneous functions with minimal
;; dependencies

(defn build-defgraft-docstring
  ([pipe]
   (str "Calls " pipe " on data and transforms it into to graph data."))
  ([pipeline graphfn]
   (str "Calls " pipeline " and transforms data into graph data by calling " graphfn)))
