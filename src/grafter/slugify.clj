(ns grafter.slugify)

;; todo think about whether protocols are a good idea here.
(defprotocol Slugger
  (slug [this & args]))

(extend-protocol Slugger
  String

  IFn

  ISeq)

(defn sluger [spec]
  (fn [row]

    ))

(comment
  (def museum-sluger (sluger "http://linked.mcr.com/" type-slug name-slug "id" id-slug))

  (museum-sluger )
