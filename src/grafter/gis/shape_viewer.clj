(ns grafter.gis.shape-viewer
  (require [clojure.java.io :as io])
  (:import [org.geotools.data CachingFeatureSource FeatureSource FileDataStore FileDataStoreFinder])
  (:import [org.geotools.map DefaultMapContext MapContext])
  (:import [org.geotools.swing JMapFrame])
  (:import [org.geotools.swing.data JFileDataStoreChooser]))

;; https://gist.github.com/gavinheavyside/415029

(defn show-shapefile
  "Prompts the user for a shapefile and displays its content"
  ([]
     (if-let [shapefile (JFileDataStoreChooser/showOpenFile "shp" nil)]
       (show-shapefile shapefile)))
  ([shapefile]
     (let [fs (.getFeatureSource (FileDataStoreFinder/getDataStore shapefile))]
         (doto (DefaultMapContext.)
           (.setTitle (str "Viewing file " shapefile))
           (.addLayer fs nil)
           (JMapFrame/showMap)))))

(defn show-shapefile-cached
  "Prompts the user for a shapefile and displays its content.
Uses memory-based cache to speed up display"
  []
  (if-let [shapefile (JFileDataStoreChooser/showOpenFile "shp" nil)]
    (let [fs (.getFeatureSource (FileDataStoreFinder/getDataStore shapefile))
          cache (CachingFeatureSource. fs)]
      (doto (DefaultMapContext.)
        (.setTitle (str "Viewing file " shapefile))
        (.addLayer cache nil)
        (JMapFrame/showMap)))))

(comment

  ;; To view a shape file run:
  (show-shapefile (io/file "./test-data/dclg-enterprise-zones/National_EZ_WGS84.shp"))
  )
