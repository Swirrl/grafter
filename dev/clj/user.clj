(ns user
  (:require [me.raynes.fs :as fs]
            [clojure.string :as string]
            [clojure.test :as test]))

(defn load-all-project-tests [& test-dirs]
  (let [test-dirs (map fs/normalized test-dirs)
        test-namespaces (->>  test-dirs
                              (mapcat (fn [td]
                                        (->> (fs/find-files td #".*\.clj$")
                                             (map #(string/replace % (str td "/") "")))))
                              (map fs/path-ns))]

    (doseq [nspc test-namespaces]
      (require nspc :verbose))

    test-namespaces))

(defn test-project []
  (apply test/run-tests (load-all-project-tests "test")))
