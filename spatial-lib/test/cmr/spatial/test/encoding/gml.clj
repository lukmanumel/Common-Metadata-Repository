(ns cmr.spatial.test.encoding.gml
  "Tests for the GML spatial encoding lib."
  (:require [clojure.data.xml :as x]
            [clojure.test :refer :all]
            [clojure.test.check :refer :all]
            [clojure.test.check.clojure-test :refer :all]
            [clojure.test.check.properties :refer :all]
            [cmr.common.xml :as cx]
            [cmr.spatial.encoding.core :as core]
            [cmr.spatial.encoding.gml :refer :all]
            [cmr.spatial.line-string :as line]
            [cmr.spatial.point :as p]
            [cmr.spatial.polygon :as poly]
            [cmr.spatial.test.generators :as spatial-gen]))

;; example XML document with valid GML elements

(def gml-xml
  "<root xmlns:gml=\"http://www.opengis.net/gml\">
     <gml:Point>
       <gml:pos>45.256 -110.45</gml:pos>
     </gml:Point>
     <gml:LineString>
       <gml:posList>
         45.256 -110.45 46.46 -109.48 43.84 -109.86 45.8 -109.2
       </gml:posList>
     </gml:LineString>
     <gml:Polygon>
       <gml:exterior>
         <gml:LinearRing>
           <gml:posList>
             45.256 -110.45 46.46 -109.48 43.84 -109.86 45.256 -110.45
           </gml:posList>
         </gml:LinearRing>
       </gml:exterior>
     </gml:Polygon>
   </root>")

(defn- emit-gml-str
  "Helper for emitting an XML document string with an xmlns attribtue
  for the gml prefix."
  [element]
  (x/emit-str (assoc-in element [:attrs :xmlns:gml] "http://www.opengis.net/gml")))

(deftest test-parse-lat-lon-string
  (testing "one point"
    (is (= (parse-lat-lon-string "9 10")
           [(p/point 10 9)])))
  (testing "multiple points"
    (is (= (parse-lat-lon-string "2 1.03 -4 3")
           [(p/point 1.03 2) (p/point 3 -4)]))))

(deftest test-encode-decode-gml
  (testing "decoding points from GML"
    (is (= (p/point -110.45 45.256)
           (core/decode :gml (cx/element-at-path (x/parse-str gml-xml) [:Point])))))
  (testing "decoding points from GML"
    (is (= (line/ords->line-string nil -110.45 45.256 -109.48 46.46 -109.86 43.84 -109.2 45.8)
           (core/decode :gml (cx/element-at-path (x/parse-str gml-xml) [:LineString]))))))

(defspec check-gml-point-round-trip 1000
  (for-all [p spatial-gen/points]
    (let [element (-> (core/encode :gml p) emit-gml-str x/parse-str)]
      (= p (core/decode :gml element)))))

(defspec check-gml-line-string-round-trip 1000
  (for-all [l spatial-gen/non-geodetic-lines]
    (let [l (assoc l :coordinate-system nil)
          element (-> (core/encode :gml l) emit-gml-str x/parse-str)]
      (= l (core/decode :gml element)))))
