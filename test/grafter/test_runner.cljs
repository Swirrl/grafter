(ns grafter.test-runner
  (:require [cljs.test :as t :include-macros true]
            [doo.runner :refer-macros [doo-tests]]
            [grafter.rdf.protocols-test]))

(doo-tests 'grafter.rdf.protocols-test)
