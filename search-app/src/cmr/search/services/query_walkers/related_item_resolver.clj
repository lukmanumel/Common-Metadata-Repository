(ns cmr.search.services.query-walkers.related-item-resolver
  "Finds RelatedItemQueryConditions in a query, executes them, processes the results and replaces
  them with the retrieved condition"
  (:require [cmr.search.models.query :as qm]
            [cmr.search.models.group-query-conditions :as gc]
            [cmr.search.data.complex-to-simple :as c2s]
            [cmr.search.data.elastic-search-index :as idx]))

(defprotocol ResolveRelatedItemQueryCondition
  (resolve-related-item-conditions
    [c context]
    "Finds and executes RelatedItemQueryConditions and replaces them with resulting conditions"))

(extend-protocol ResolveRelatedItemQueryCondition
  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.search.models.query.Query

  (resolve-related-item-conditions
    [query context]
    (update-in query [:condition] #(resolve-related-item-conditions % context)))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.search.models.query.ConditionGroup

  (resolve-related-item-conditions
    [condition context]
    (update-in condition [:conditions] (partial mapv #(resolve-related-item-conditions % context))))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  cmr.search.models.query.RelatedItemQueryCondition

  (resolve-related-item-conditions
    [{:keys [concept-type condition result-fields results-to-condition-fn]} context]
    (->> (qm/query {:concept-type concept-type
                    :condition condition
                    :page-size :unlimited
                    :result-format :query-specified
                    :fields result-fields})
         (c2s/reduce-query context)
         (idx/execute-query context)
         results-to-condition-fn))

  ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  ;; catch all resolver
  java.lang.Object
  (resolve-related-item-conditions [this context] this))