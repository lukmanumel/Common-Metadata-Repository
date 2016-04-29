(ns cmr.umm-spec.location-keywords
  "Helper utilities for converting GCMD Spatial Keywords -> UMM LocationKeywords"
  (:require [cmr.common-app.services.kms-fetcher :as kf]
            [cmr.umm-spec.models.collection :as umm-c]))

(def duplicate-keywords
  "Temporary lookup table to account for any duplicate keywords. Key is :uuid which is a field in
  the location-keyword map. "
  {"BLACK SEA" {:uuid "afbc0a01-742e-49da-939e-3eaa3cf431b0"}
   "SPACE" {:uuid "6f2c3b1f-acae-4af0-a759-f0d57ccfc83f"}})

(defn spatial-keywords-map
  [full-map]
  "Returns a list of maps that correspond to the spatial keywords hierarchy of the full map of GCMD keywords.
  Keys are :category :type :subregion-1 :subregion-2 :subregion-3 :uuid. Values are single string-type.
  Not all keys are present in every map."
  (:spatial-keywords full-map))

(defn find-spatial-keyword-in-map
  "Finds a spatial keyword in the gcmd spatial-keywords map.
  Takes a string keyword as a parameter, e.g. 'OCEAN' and a list of spatial keyword maps;
  returns a list of maps of hierarchies which contain the keyword."
  [keyword-map-list keyword]
  (filter (fn [map] (some #{keyword} (vals map))) keyword-map-list))

(defn find-spatial-keyword
  "Finds spatial keywords in the heirarchy and pick the one with the fewest keys
  (e.g. highest hierarchical depth.) Takes a string keyword as a parameter (assumes no case) a list
  of keyword maps and returns the map of hierarichies which contain the keyword.
  You can also look by entering the uuid of the entry if you know it."
  [keyword-map-list keyword]
  (if (contains? duplicate-keywords keyword)
  (first (find-spatial-keyword-in-map keyword-map-list (:uuid (get duplicate-keywords "SPACE"))))
  (first (sort-by count (find-spatial-keyword-in-map keyword-map-list keyword)))))

(defn spatial-keywords->location-keywords
  "Takes a context and a list of Spatial Keywords and returns a list of location keyword maps
  for that context"
  [keyword-map-list spatial-keywords]
  (map #(find-spatial-keyword keyword-map-list %) spatial-keywords))

(defn translate-spatial-keywords
  "Translates a list of spatial keywords into an array of LocationKeyword type objects"
  [context spatial-keywords]
  (let [spatial-keyword-maps (spatial-keywords-map (kf/get-gcmd-keywords-map context))
        location-keyword-maps (spatial-keywords->location-keywords spatial-keyword-maps spatial-keywords)]
    (map #(umm-c/map->LocationKeywordType %) location-keyword-maps))
  )

;; Examples
(comment
  (def local-test-system-context
    {:system (get-in user/system [:apps :indexer])})

  (find-spatial-keyword  (spatial-keywords-map (kf/get-gcmd-keywords-map local-test-system-context)) "a028edce-a3d9-4a16-a8c7-d2cb12d3a318")
  (find-spatial-keyword  (spatial-keywords-map (kf/get-gcmd-keywords-map local-test-system-context)) "CENTRAL AFRICA")
  (find-spatial-keyword-in-map  (spatial-keywords-map (kf/get-gcmd-keywords-map local-test-system-context)) "SPACE")
)
