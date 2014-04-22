(ns grafter.gui.graph
  (:use [seesaw.core]
        [seesaw.table])
  (:import [grafter.gui GraphPanel])
  (:require [seesaw.selector :as selector]))

(comment (do (use 'seesaw.dev)
             (use 'clojure.repl)))

(defn identify
  "Make names from Eclipse GUI builder act as seesaw ids.  As described at:
https://github.com/daveray/seesaw/wiki/Window-Builder"
  [root]
  (doseq [w (select root [:*])]
    (if-let [n (.getName w)]
      (selector/id-of! w (keyword n))))
  root)

(defn make-table-frame [data-model]
  (let [table-frame (frame :title "Grafter"
                           :content (identify (GraphPanel.)))]

    (-> (select table-frame [:JTable])
        first
        (config! :model data-model))
    table-frame))

(def data (table-model :columns [:name :age]
                       :rows (map vector
                                  (take 1000 (cycle ["Rick" "Bob" "Katie"]))
                                  (iterate inc 0))))

(defn start []
  (-> (make-table-frame data)
      pack!
      show!))

(comment
  (def main-frame (start)))
