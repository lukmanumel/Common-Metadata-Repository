(ns cmr.umm-spec.xml-to-umm-mappings.iso-shared.distributions-related-url
  "Functions for parsing UMM related-url records out of ISO XML documents."
  (:require
   [clojure.string :as str]
   [cmr.common.util :as util]
   [cmr.common.xml.parse :refer :all]
   [cmr.common.xml.simple-xpath :refer :all]
   [cmr.umm-spec.iso19115-2-util :refer :all]
   [cmr.umm-spec.url :as url]
   [cmr.umm-spec.util :as su]))

(defn get-index-or-nil
 "Get the index of the key in the description. Return nil if the key does not
 exist in the description"
 [description key]
 (let [index (.indexOf description key)]
  (when (>= index 0)
   index)))

(defn get-substring
  "Get a substring from the input string. Use start as the starting index. All of the rest
   of the input parameters are stop indexes in numerical order or nil i.e. (34 nil 50). The
   function uses the first non nil value as the stop value. The function returns the
   substring with trimmed whitespace before and after the string."
  [str start & args]
  (let [value (subs str start (some #(when % %) args))]
    (str/trim (subs value (inc (.indexOf value ":"))))))

(defn parse-url-types-from-description
 "In ISO, since there are not separate fields for the types, they are put in the
 description in the format 'Description: X URLContentType: Y Type: Z Subtype: A'
 Parse all available elements out from the description string."
 [description]
 (when description
  (let [description-index (get-index-or-nil description "Description:")
        url-content-type-index (get-index-or-nil description "URLContentType:")
        type-index (get-index-or-nil description " Type:")
        subtype-index (get-index-or-nil description "Subtype:")
        checksum-index (get-index-or-nil description "Checksum:")]
   (if (and (nil? description-index)(nil? url-content-type-index)
            (nil? type-index) (nil? subtype-index))
    {:Description description} ; Description not formatted like above, so just description
    {:Description (when description-index
                   (let [desc (subs description
                               description-index
                               (or url-content-type-index type-index subtype-index
                                   checksum-index (count description)))]
                    (str/trim (subs desc (inc (.indexOf desc ":"))))))
     :URLContentType (when url-content-type-index
                      (let [content-type (subs description
                                          url-content-type-index
                                          (or type-index subtype-index checksum-index (count description)))]
                        (str/trim (subs content-type (inc (.indexOf content-type ":"))))))
     :Type (when type-index
            (let [type (subs description
                        type-index
                        (or subtype-index checksum-index (count description)))]
              (str/trim (subs type (inc (.indexOf type ":"))))))
     :Subtype (when subtype-index
                (let [subtype (subs description
                               subtype-index
                               (or checksum-index (count description)))]
                  (str/trim (subs subtype (inc (.indexOf subtype ":"))))))
     :Checksum (when checksum-index
                 (let [checksum (subs description checksum-index)]
                   (str/trim (subs checksum (inc (.indexOf checksum ":"))))))}))))

(defn- parse-operation-description
  "Parses operationDescription string, returns MimeType, DataID, and DataType"
  [operation-description]
  (when operation-description
    (let [mime-type-index (get-index-or-nil operation-description "MimeType:")
          data-id-index (get-index-or-nil operation-description "DataID:")
          data-type-index (get-index-or-nil operation-description "DataType:")
          format-index (get-index-or-nil operation-description "Format:")]
      {:MimeType (when mime-type-index
                   (let [mime-type (subs operation-description
                                    mime-type-index
                                    (or data-id-index data-type-index
                                        format-index (count operation-description)))]
                     (str/trim (subs mime-type (inc (.indexOf mime-type ":"))))))
       :DataID (when data-id-index
                 (let [data-id (subs operation-description
                                     data-id-index
                                     (or data-type-index format-index (count operation-description)))]
                   (str/trim (subs data-id (inc (.indexOf data-id ":"))))))
       :DataType (when data-type-index
                   (let [data-type (subs operation-description
                                         data-type-index
                                         (or format-index (count operation-description)))]
                     (str/trim (subs data-type (inc (.indexOf data-type ":"))))))
       :Format (when format-index
                 (let [format (subs operation-description format-index)]
                   (str/trim (subs format (inc (.indexOf format ":"))))))})))

(defn parse-service-urls
  "Parse service URLs from service location. These are most likely dups of the
  distribution urls, but may contain additional type info."
  [doc sanitize? service-url-path service-online-resource-xpath]
  (for [service (select doc service-url-path)
        :let [local-name (value-of service "srv:serviceType/gco:LocalName")]
        :when (str/includes? local-name "RelatedURL")
        :let [url-types (parse-url-types-from-description local-name)
              uris (mapv #(value-of % "gmd:linkage/gmd:URL")
                         (select service service-online-resource-xpath))
              url (first (select service service-online-resource-xpath))
              url-link (value-of url "gmd:linkage/gmd:URL")
              full-name (value-of service "srv:containsOperations/srv:SV_OperationMetadata/srv:operationName/gco:CharacterString")
              protocol (value-of url "gmd:protocol/gco:CharacterString")
              description (char-string-value url "gmd:description")
              ;;http is currently invalid in the schema, use HTTP instead
              protocol (if (= "http" protocol)
                         "HTTP"
                         protocol)
              operation-description (value-of service "srv:containsOperations/srv:SV_OperationMetadata/srv:operationDescription/gco:CharacterString")
              {:keys [MimeType DataID DataType Format]} (parse-operation-description operation-description)]]
    (merge url-types
           {:URL (when url-link (url/format-url url-link sanitize?))
            :Description (when (seq description)
                           (str/trim description))} 
           (util/remove-nil-keys
            {:GetService (when (or MimeType full-name DataID protocol DataType Format
                                  (not (empty? uris)))
                           {:MimeType (su/with-default MimeType sanitize?)
                            :FullName (su/with-default full-name sanitize?)
                            :DataID (su/with-default DataID sanitize?)
                            :Protocol (su/with-default protocol sanitize?)
                            :DataType (su/with-default DataType sanitize?)
                            :Format Format
                            :URI (when-not (empty? uris)
                                   uris)})}))))

(defn- parse-distributor-format
  "Parses distributor format string, returns MimeType and Format"
  [distributor distributor-xpaths-map]
  (when-let [format-name (value-of distributor (get distributor-xpaths-map :Format))]
    (let [mime-type-index (get-index-or-nil format-name "MimeType:")
          format-index (get-index-or-nil format-name "Format:")]
      {:Format (when format-index
                 (let [format (subs format-name
                                    format-index
                                    (or mime-type-index (count format-name)))]
                   (str/trim (subs format (inc (.indexOf format ":"))))))
       :MimeType (when mime-type-index
                   (let [mime-type (subs format-name mime-type-index)]
                     (str/trim (subs mime-type (inc (.indexOf mime-type ":"))))))})))

(defn parse-online-urls
  "Parse ISO online resource urls"
  [doc sanitize? service-urls distributor-xpaths-map]
  (for [distributor (select doc (get distributor-xpaths-map :Root))
        :let [size (value-of distributor (str (get distributor-xpaths-map :TransferOptions)
                                              "/gmd:transferSize"))
              unit (value-of distributor (str (get distributor-xpaths-map :TransferOptions)
                                              "/gmd:unitsOfDistribution/gco:CharacterString"))
              fees (value-of distributor (get distributor-xpaths-map :Fees))
              url (get distributor-xpaths-map :URL)
              format (parse-distributor-format distributor distributor-xpaths-map)
              name (char-string-value distributor (str url "gmd:name"))
              code (value-of distributor (str url "gmd:function/gmd:CI_OnlineFunctionCode"))
              url-link (value-of distributor (str url "gmd:linkage/gmd:URL"))
              url-link (when url-link (url/format-url url-link sanitize?))
              opendap-type (when (= code "GET DATA : OPENDAP DATA (DODS)")
                            "GET SERVICE")
              types-and-desc (parse-url-types-from-description
                              (char-string-value distributor (str url "gmd:description")))
              service-url (first (filter #(= url-link (:URL %)) service-urls))
              type (or opendap-type (:Type types-and-desc) (:Type service-url))]
        :when (seq type)]
    (merge
     {:URL url-link
      :URLContentType "DistributionURL"
      :Type type
      :Subtype (if opendap-type
                "OPENDAP DATA (DODS)"
                (or (:Subtype types-and-desc) (:Subtype service-url)))
      :Description (:Description types-and-desc)}
     (case type
       "GET DATA" {:GetData {:Format (su/with-default (:Format format) sanitize?)
                             :Size (if sanitize?
                                     (or size 0.0)
                                     size)
                             :Unit (if sanitize?
                                     (or unit "KB")
                                     unit)
                             :Fees fees
                             :Checksum (:Checksum types-and-desc)
                             :MimeType (:MimeType format)}}
       nil))))

(defn parse-online-and-service-urls
  "Parse online and service urls. Service urls may be a dup of distribution urls,
  but may contain additional needed type information. Filter out dup service URLs."
  [doc sanitize? service-url-path distributor-xpaths-map service-online-resource-xpath]
  (let [service-urls (parse-service-urls doc sanitize? service-url-path service-online-resource-xpath)
        online-urls (parse-online-urls doc sanitize? service-urls distributor-xpaths-map)
        online-url-urls (set (map :URL online-urls))]
    (concat
     online-urls
     service-urls)))

(defn parse-browse-graphics
  "Parse browse graphic urls"
  [doc sanitize? browse-graphic-xpath]
  (for [url (select doc browse-graphic-xpath)
        ;; We retrieve browse url from two different places. This might change depending on the
        ;; outcome of ECSE-129.
        :let [browse-url (or (value-of url "gmd:fileName/gmx:FileName/@src")
                             (value-of url "gmd:fileName/gco:CharacterString"))
              types-and-desc (parse-url-types-from-description
                              (char-string-value url "gmd:fileDescription"))]]
     {:URL (when browse-url (url/format-url browse-url sanitize?))
      :Description (:Description types-and-desc)
      :URLContentType "VisualizationURL"
      :Type "GET RELATED VISUALIZATION"
      :Subtype (:Subtype types-and-desc)}))

(defn parse-publication-urls
 "Parse PublicationURL and CollectionURL from the publication location."
 [doc sanitize? publication-url-path]
 (for [url (select doc publication-url-path)
       :let [description (char-string-value url "gmd:description")]
       :when (and (some? description)
                  (not (str/includes? description "PublicationReference:")))
       :let [types-and-desc (parse-url-types-from-description description)
             url-content-type (or (:URLContentType types-and-desc) "PublicationURL")]]
  {:URL (value-of url "gmd:linkage/gmd:URL")
   :Description (:Description types-and-desc)
   :URLContentType (or (:URLContentType types-and-desc) "PublicationURL")
   ;; Types are validated against the URLContentType, so if URLContentType is
   ;; CollectionURL then use the valid DATA SET LANDING PAGE type, otherwise use
   ;; VIEW RELATED INFORMATION which is valid for PublicationURL
   :Type (or (:Type types-and-desc) (if (= (:URLContentType types-and-desc) "CollectionURL")
                                      "DATA SET LANDING PAGE"
                                      "VIEW RELATED INFORMATION"))
   :Subtype (:Subtype types-and-desc)}))
