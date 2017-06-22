(ns gnowdb.neo4j.gneo
  (:gen-class)
  (:require [clojure.set :as clojure.set]
            [clojure.java.io :as io]
            [clojure.string :as clojure.string]
            [gnowdb.neo4j.gdriver :as gdriver]
            [gnowdb.neo4j.gcust :as gcust]))

(defn addStringToMapKeys
  [stringMap string]
  {:pre [(string? string)
         (map? stringMap)]}
  (if (empty? stringMap)
    {}
    (apply conj
           (map
            (fn
              [[stringKey value]]
              {(str stringKey string) value}
              )
            stringMap
            )
           )
    )
  )

(defn- removeVectorStringSuffixes
  "Removes the string suffix from the Vector members"
  [mapKeyVector stringSuffix]
  (
   into []
   (
    map (fn
          [keyValue]
          (clojure.string/replace keyValue (java.util.regex.Pattern/compile (str stringSuffix "$")) "")
          )
    mapKeyVector
    )
   )
  )

(defn- createParameterPropertyString
  "Create Property String with parameter fields using map keys"
  [propertyMap & [characteristicString]]
  ;;The characteristicString is sometimes appended to map keys to distinguish
  ;;the keys when multiple maps and their keys are used in the same cypher
  ;;query with parameters
  (if
      (empty? propertyMap)
    ""
    (str "{ "
         (clojure.string/join ", " 
                              (vec 
                               (map #(str %1 ":{" %2 "}")
                                    (removeVectorStringSuffixes (vec (keys propertyMap)) characteristicString)
                                    (vec (keys propertyMap))
                                    )
                               )
                              )
         " }"
         )
    )
  )

(defn- combinePropertyMap
  "Combine PropertyMaps and associated propertyStrings.
  Name PropertyMaps appropriately.
  Input PropertyMaps as map of maps.
  Keys Should be strings"
  [propertyMaps]
  {:combinedPropertyMap (reduce
                         #(if
                              (empty? (%2 1))
                            %1
                            (merge %1
                                   (addStringToMapKeys
                                    (%2 1)
                                    (%2 0)
                                    )
                                   )
                            )
                         {}
                         (seq propertyMaps)
                         )
   :propertyStringMap (reduce
                       #(assoc
                         %1
                         (%2 0)
                         (if
                             (empty? (%2 1))
                           ""
                           (createParameterPropertyString (addStringToMapKeys (%2 1) (%2 0)) (%2 0))
                           )
                         )
                       {}
                       (seq propertyMaps)
                       )
   }
  )

(defn createEditString
  "Creates an edit string.
  eg.., SET varName.prop1={prop1} , varName.prop2={prop2}
  :varName should be name of the node/relation variable.
  :editPropertyList should be a collection of properties."
  [& {:keys [:varName
             :editPropertyList
             :characteristicString]
      :or {:characteristicString ""}}]
  {:pre [(string? varName)
         (coll? editPropertyList)
         (every? string? editPropertyList)]}
  (str " SET "
       (clojure.string/join " ,"
                            (map #(str varName "." %1 " = {" %2 "}")
                                 (removeVectorStringSuffixes editPropertyList characteristicString)
                                 editPropertyList)
                            )
       )
  )

(defn createRemString
  "Creates a property removal string.
  eg.., REMOVE  varName.prop1 ,varName.prop2.
  :varName should be a string representing node/relation variable.
  :remPropertyList should be collection of properties for removal"
  [& {:keys [:varName :remPropertyList]}]
  {:pre [(string? varName)
         (coll? remPropertyList)
         (every? string? remPropertyList)]}
  (str "REMOVE "
       (clojure.string/join ", "
                            (vec (map #(str varName"."%1) 
                                      remPropertyList
                                      )	
                                 )
                            )
       )
  )

(defn createRenameString
  "Creates a property rename string.
  eg.., WHERE varName.prop1R is null and varName.prop2R is null .. SET varName.prop1R=varName.prop1, varName.prop1R=varName.prop1 REMOVE varName.prop1 ,varName.prop2
  :varName should be a string representing node/relation variable.
  :renameMap should be a map with keys as propertyNames and values as newNames.
  :addWhere? boolean, whether the keyword WHERE is to be included"
  [& {:keys [:varName
             :renameMap
             :addWhere?]
      :or {:addWhere? true}}]
  {:pre [(string? varName)
         (not (empty? renameMap))
         (every? string? (keys renameMap))
         (every? string? (vals renameMap))
         ]
   }
  (str (if addWhere? "WHERE ") (clojure.string/join " and "
                                                    (map #(str varName"."%" is null")
                                                         (vals renameMap)
                                                         )
                                                    )
       " SET " (clojure.string/join ", "
                                    (map #(str varName"."(% 1)"="varName"."(% 0))
                                         (into [] renameMap)
                                         )
                                    )
       " " (createRemString :varName varName
                            :remPropertyList (keys renameMap))
       )
  )

(defn editCollection
  "Edits a collection of strings to represent edited property from createPropListEditString.
  :coll should be a collection of strings
  :editType should be one of APPEND,DELETE,REPLACE.
  :editVal should be parameter representing value for APPEND/DELETE/REPLACE.
  :replaceVal should be parameter representing intended value, if :editVal is REPLACE"
  [& {:keys [:coll
             :editType
             :editVal
             :replaceVal]
      :or [:replaceVal ""]}]
  {:pre [(coll? coll)
         (every? string? coll)
         (contains? #{"APPEND" "DELETE" "REPLACE"} editType)
         (string? editVal)
         (or (string? replaceVal) (not= "REPLACE" editType))]}
  (case editType
    "APPEND" (conj coll editVal)
    "DELETE" (remove #(= editVal %) coll)
    "REPLACE" (concat (remove #(= editVal %) coll) [replaceVal])
    )
  )

(defn createPropListEditString
  "Creates a string that edits a property that is a list, by append/delete/replace an element.
  :varName should be string.
  :propName should be string, representing the propertyName.
  :editType should be one of APPEND,DELETE,REPLACE.
  :editVal should be parameter representing value for APPEND/DELETE/REPLACE.
  :replaceVal should be parameter representing intended value, if :editVal is REPLACE.
  :withWhere? should be true if Where condition should be included."
  [& {:keys [:varName
             :propName
             :editType
             :editVal
             :replaceVal
             :withWhere?]
      :or [:replaceVal ""
           :withWhere? true]}]
  {:pre [(string? varName)
         (string? propName)
         (contains? #{"APPEND" "DELETE" "REPLACE"} editType)
         (string? editVal)
         (or (string? replaceVal) (not= "REPLACE" editType))
         ]
   }
  (str 
   (case editType
     "APPEND" (str " SET "varName"."propName
                   " = " varName"."propName" + {"editVal"}")
     "DELETE" (str (if withWhere? (str "WHERE {"editVal"} IN "varName"."propName) "")" SET "varName"."propName
                   " = FILTER(x IN "varName"."propName" WHERE x <> {"editVal"})")
     "REPLACE" (str (if withWhere? (str "WHERE {"editVal"} IN "varName"."propName) "")" SET "varName"."propName
                    " = FILTER(x IN "varName"."propName" WHERE x <> {"editVal"}) + {"replaceVal"}")
     )
   )
  )

;;General NEO4J functions start here

(defn generateUUID
  []
  (str (java.util.UUID/randomUUID))
  )

(defn getAllLabels
  "Get all the Labels from the graph, parsed."
  []
  (map #(% "LABELS(n)") (((gdriver/runQuery {:query "MATCH (n) RETURN DISTINCT LABELS(n)" :parameters {}}) :results) 0)
       )
  )

(defn getAllNodes
  "Returns a lazy sequence of labels and properties of all nodes in the graph"
  []
  (map #(% "n") (((gdriver/runQuery {:query "MATCH (n) RETURN n" :parameters {}}) :results) 0))
  )

(defn createNewNode
  "Create a new node in the graph. Without any relationships.
  Node properties should be a clojure map.
  Map keys will be used as neo4j node keys.
  Map keys should be Strings only.
  Map values must be neo4j compatible Objects"
  [& {:keys [:label
             :parameters
             :execute?
             :unique?]
      :or {:execute? true
           :unique? false
           :parameters {}}
      :as keyArgs
      }
   ]
  (let [queryType 
 	(if unique?
          "MERGE"
          "CREATE"
          )
        mergedParams (merge parameters {"UUID" (generateUUID)})
  	builtQuery  	{:query (str queryType " (node:" label " "
                                     (createParameterPropertyString
                                      mergedParams) " )")
                         :parameters mergedParams}
        ]
    (if execute?
      ((gdriver/runQuery builtQuery) :summary)
      builtQuery
      )
    )
  )  

(defn deleteRelation
  "Delete a relation between two nodes matched with their properties (input as clojure map) with it's own properties"
  [& {:keys [:fromNodeLabel
             :fromNodeParameters
             :relationshipType
             :relationshipParameters
             :toNodeLabel
             :toNodeParameters
             :execute?]
      :or {:execute? true
           :toNodeParameters {}
           :fromNodeParameters {}
           :relationshipParameters {}}}]
  {:pre [
         (every? string? [fromNodeLabel relationshipType toNodeLabel])
         (every? map? [fromNodeParameters relationshipParameters toNodeParameters])]}
  (let [combinedProperties
        (combinePropertyMap
         {"1" fromNodeParameters
          "2" toNodeParameters
          "R" relationshipParameters
          }
         )
        builtQuery
        {:query
         (str "MATCH (node1:"fromNodeLabel" "
              ((combinedProperties :propertyStringMap) "1")
              " )-[rel:"relationshipType" "
              ((combinedProperties :propertyStringMap) "R")
              " ]->(node2:"toNodeLabel" "
              ((combinedProperties :propertyStringMap) "2")
              " )  DELETE rel")
         :parameters (combinedProperties :combinedPropertyMap)}]
    (if execute?
      (gdriver/runQuery builtQuery)
      builtQuery
      )
    )
  )

(defn editRelation
  "Edit Parameters of a relation"
  [& {:keys [:fromNodeLabel
             :fromNodeParameters
             :relationshipType
             :relationshipParameters
             :toNodeLabel
             :toNodeParameters
             :newRelationshipParameters
             :execute?]
      :or {:execute? true
           :toNodeParameters {}
           :fromNodeParameters {}
           :relationshipParameters {}}
      }
   ]
  {:pre [
         (every? string? [fromNodeLabel relationshipType toNodeLabel])
         (every? map? [fromNodeParameters relationshipParameters toNodeParameters])
         (map? newRelationshipParameters)]}
  (let [combinedProperties
        (combinePropertyMap
         {"1" fromNodeParameters
          "2" toNodeParameters
          "R" relationshipParameters
          "RE" newRelationshipParameters
          }
         )
        builtQuery
        {:query
         (str "MATCH (node1:"fromNodeLabel" "
              ((combinedProperties :propertyStringMap) "1")
              " )-[rel:"relationshipType" "
              ((combinedProperties :propertyStringMap) "R")
              " ]->(node2:"toNodeLabel" "
              ((combinedProperties :propertyStringMap) "2")
              " ) "(createEditString :varName "rel"
                                     :editPropertyList (keys (addStringToMapKeys newRelationshipParameters "RE"))
                                     :characteristicString "RE")
              )
         :parameters (combinedProperties :combinedPropertyMap)}]
    (if execute?
      (gdriver/runQuery builtQuery)
      builtQuery
      )
    )
  )

(defn editRelationPropList
  "Edit Parameter of a relation that is a List
  :propName should be string, representing the propertyName.
  :editType should be one of APPEND,DELETE,REPLACE.
  :editVal should represent value for APPEND/DELETE/REPLACE.
  :replaceVal should be intended value, if :editVal is REPLACE"
  [& {:keys [:fromNodeLabel
             :fromNodeParameters
             :relationshipType
             :relationshipParameters
             :toNodeLabel
             :toNodeParameters
             :propName
             :editType
             :editVal
             :replaceVal
             :execute?]
      :or {:execute? true
           :toNodeParameters {}
           :fromNodeParameters {}
           :relationshipParameters {}}
      }
   ]
  {:pre [
         (every? string? [fromNodeLabel relationshipType toNodeLabel])
         (every? map? [fromNodeParameters relationshipParameters toNodeParameters])]}
  (let [combinedProperties
        (combinePropertyMap
         {"1" fromNodeParameters
          "2" toNodeParameters
          "R" relationshipParameters
          }
         )
        builtQuery
        {:query
         (str "MATCH (node1:"fromNodeLabel" "
              ((combinedProperties :propertyStringMap) "1")
              " )-[rel:"relationshipType" "
              ((combinedProperties :propertyStringMap) "R")
              " ]->(node2:"toNodeLabel" "
              ((combinedProperties :propertyStringMap) "2")
              " ) "(createPropListEditString :varName "rel"
                                             :propName propName
                                             :editType editType
                                             :editVal "ATT"
                                             :replaceVal "att")
              )
         :parameters (merge {"ATT" editVal
                             "att" replaceVal}
                            (combinedProperties :combinedPropertyMap)
                            )
         }
        ]
    (if execute?
      (gdriver/runQuery builtQuery)
      builtQuery
      )
    )
  )

(defn createRelation
  "Relate two nodes matched with their properties (input as clojure map) with it's own properties"
  [& {:keys [:fromNodeLabel
             :fromNodeParameters
             :relationshipType
             :relationshipParameters
             :toNodeLabel
             :toNodeParameters
             :execute?
             :unique?]
      :or {:execute? true
           :unique? false
           :toNodeParameters {}
           :fromNodeParameters {}
           :relationshipParameters {}}
      }
   ]
  {:pre [
         (every? string? [fromNodeLabel relationshipType toNodeLabel])
         (every? map? [fromNodeParameters relationshipParameters toNodeParameters])]}
  (let [combinedProperties
        (combinePropertyMap
         {"1" fromNodeParameters
          "2" toNodeParameters
          "R" relationshipParameters
          }
         )
        unique
        (if unique?
          "UNIQUE"
          ""
          )
        builtQuery
        {:query
         (str "MATCH (node1:" fromNodeLabel " "
              ((combinedProperties :propertyStringMap) "1")
              " ) , (node2:" toNodeLabel " "
              ((combinedProperties :propertyStringMap) "2")
              " ) CREATE " unique " (node1)-[:" relationshipType " "
              ((combinedProperties :propertyStringMap) "R")
              " ]->(node2)")
         :parameters (combinedProperties :combinedPropertyMap)}]
    (if execute?
      (gdriver/runQuery builtQuery)
      builtQuery
      )
    )
  )

(defn deleteDetachNodes
  "Delete node(s) matched using property map and detach (remove relationships)"
  [& {:keys [:label
             :parameters
             :execute?]
      :or {:execute? true
           :parameters {}}
      }
   ]
  (let [builtQuery {:query (str "MATCH (node:" label " "
                                (createParameterPropertyString parameters)
                                " ) DETACH DELETE node")
                    :parameters parameters}
        ]
    (if
        execute?
      (gdriver/runQuery builtQuery)
      builtQuery
      )
    )
  )

(defn deleteNodes
  "Delete node(s) matched using property map"
  [& {:keys [:label
             :parameters
             :execute?]
      :or {:execute? true
           :parameters {}}
      }
   ]
  (let [builtQuery {:query (str "MATCH (node:"label" "
                                (createParameterPropertyString parameters)
                                " ) DELETE node")
                    :parameters parameters}
        ]
    (if
        execute?
      (gdriver/runQuery builtQuery)
      builtQuery
      )
    )
  )



(defn removeNodeProperties
  "Remove Properties of Node(s).
  :propList should be a list of properties to be deleted."
  [& {:keys [:label
             :parameters
             :propList
             :execute?]
      :or {:propList {}
           :execute? true
           :parameters {}}
      }
   ]
  {:pre [(string? label)
         (map? parameters)
         (coll? propList)
         (every? string? propList)]}
  (let [builtQuery {:query (str "MATCH (node:"label" "
                                (createParameterPropertyString parameters)
                                " ) "(createRemString :varName "node"
                                                      :remPropertyList propList)
                                )
                    :parameters parameters
                    }
        ]
    (if
        execute?
      (gdriver/runQuery builtQuery)
      builtQuery
      )
    )
  )

(defn editNodeProperties
  "Edit Properties of Node(s)"
  [& {:keys [:label
             :parameters
             :changeMap
             :execute?]
      :or {:changeMap {}
           :execute? true
           :parameters {}}
      }
   ]
  (let [mPM (addStringToMapKeys parameters "M")
        tPME (addStringToMapKeys changeMap "E")
        builtQuery {:query (str "MATCH (node1:" label " "
                                (createParameterPropertyString mPM "M")
                                " ) "(createEditString :varName "node1"
                                                       :editPropertyList (keys tPME)
                                                       :characteristicString "E"
                                                       )
                                )
                    :parameters (merge mPM tPME)
                    }
        ]
    (if
        execute?
      (gdriver/runQuery builtQuery)
      builtQuery)
    )
  )

(defn renameNodeProperties
  "Rename node(s) properties."
  [& {:keys [:label
             :parameters
             :renameMap
             :execute?]
      :or {:execute? true
           :parameters {}}
      }
   ]
  {:pre [(string? label)
         (map? parameters)
         (map renameMap)]}
  (let [builtQuery
        {:query (str "MATCH (node:" label " "
                     (createParameterPropertyString parameters) " ) "
                     (createRenameString :varName "node"
                                         :renameMap renameMap))
         :parameters {}}
        ]
    (if
        execute?
      (gdriver/runQuery builtQuery)
      builtQuery)
    )
  )

(defn editNodePropList
  "Edits a property of a node with a list as a value.
  :label should be string.
  :parameters should be a map.
  :propName should be string, representing the propertyName.
  :editType should be one of APPEND,DELETE,REPLACE.
  :editVal should represent value for APPEND/DELETE/REPLACE.
  :replaceVal should be intended value, if :editVal is REPLACE"
  [& {:keys [:label
             :parameters
             :propName
             :editType
             :editVal
             :replaceVal
             :execute?]
      :or [:replaceVal nil
           :parameters {}
           :execute? true]
      }
   ]
  {:pre [(string? label)
         (map? parameters)]}
  (let [combinedPropertyMap (combinePropertyMap {"NP" parameters})
        builtQuery {:query (str "MATCH (node:"label" "
                                ((combinedPropertyMap :propertyStringMap) "NP")
                                ") "(createPropListEditString :varName "node"
                                                              :propName propName
                                                              :editType editType
                                                              :editVal "ATT"
                                                              :replaceVal "att")
                                )
                    :parameters (merge {"ATT" editVal "att" replaceVal}
                                       (combinedPropertyMap :combinedPropertyMap)
                                       )
                    }
        ]
    (if execute?
      (gdriver/runQuery builtQuery)
      builtQuery
      )
    )
  )

(defn removeLabels
  "Removes labels from a node
  :remLabelList should be a list of strings"
  [& {:keys [:label
             :properties
             :remLabelList
             :execute?]
      :or {:properties {}}
      :execute? true}]
  {:pre [(string? label)
         (map? properties)
         (coll? remLabelList)
         (every? string? remLabelList)
         (not (empty? remLabelList))]}
  (let [builtQuery {:query (str "MATCH (obj:"label" "(createParameterPropertyString properties)") "
                                (clojure.string/join " " (map #(str "REMOVE obj:"%) remLabelList)
                                                     )
                                )
                    :parameters properties}]
    (if execute?
      (gdriver/runQuery builtQuery)
      builtQuery)
    )
  )

(defn renameLabels
  "Renames label(s) of a node/relation.
  :objectType should be NODE or RELATION.
  :replaceLabelMap should be a map of strings, with keys as existing labels and the values as newLabels.
  if a label with a particular key doesnt exist, the new label will be added.
  if :objectType is RELATION, remLabelList can only have one string"
  [& {:keys [:label
             :properties
             :objectType
             :replaceLabelMap
             :execute?]
      :or {:properties {}
           :objectType "NODE"
           :execute? true}}]
  {:pre [(string? label)
         (map? properties)
         (map? replaceLabelMap)
         (every? string? (vals replaceLabelMap))
         (every? string? (keys replaceLabelMap))
         (not (empty? replaceLabelMap))
         (or (= "NODE" objectType)
             (and (= "RELATION" objectType)
                  (= 1 (count replaceLabelMap))
                  (not (nil? (replaceLabelMap label)))
                  )
             )
         ]
   }
  (let [builtQuery {:query (case objectType
                             "NODE" (str "MATCH (obj:"label" "(createParameterPropertyString properties)") "
                                         (clojure.string/join " " (map #(str "REMOVE obj:"(% 0)" "
                                                                             "SET obj:"(% 1)) replaceLabelMap)
                                                              )
                                         )
                             "RELATION" (str "MATCH (n1)-[rel:"label"]->(n2)"
                                             " MERGE (n1)-[rel2:"(replaceLabelMap label)"]-(n2)"
                                             " SET rel2=rel"
                                             " WITH rel"
                                             " DELETE rel")
                             )
                    :parameters properties}]
    (if execute?
      (gdriver/runQuery builtQuery)
      builtQuery)
    ))

(defn getNodeByUUID
  "Get Node by UUID"
  [& {:keys [:UUID]}]
  {:pre [(string? UUID)]}
  (let [params {"UUID" UUID}
        ret (first ((gdriver/runQuery {:query (str "MATCH (n "(createParameterPropertyString params)") RETURN n")
                       :parameters params}
                                      ) :results))]
    (if (empty? ret)
        nil
        ((first ret) "n"))
    )
  )

(defn getNodes
  "Get Node(s) matched by label and propertyMap"
  [& {:keys [:label
             :parameters
             :count?
             :execute?]
      :or {:parameters {}
           :count? false}}]
  {:pre [(string? label)]}
  (map #(% (if count?
             "count(node)"
             "node")) (((gdriver/runQuery {:query (str "MATCH (node:" label " "
                                                       (createParameterPropertyString parameters)
                                                       ") RETURN " (if count?
                                                                     "count(node)"
                                                                     "node"))
                                           :parameters parameters})
                        :results) 0)
       )
  )

(defn getRelations
  "Get relations matched by inNode/outNode/type and properties"
  [& {:keys [:fromNodeLabel
             :fromNodeParameters
             :relationshipType
             :relationshipParameters
             :toNodeLabel
             :toNodeParameters
             :count?
             :execute?
             :nodeInfo?]
      :or {:execute? true
           :toNodeParameters {}
           :fromNodeParameters {}
           :relationshipParameters {}
           :fromNodeLabel ""
           :toNodeLabel ""
           :relationshipType ""
           :nodeInfo? false}}]
  (let [combinedProperties
        (combinePropertyMap
         {"1" fromNodeParameters
          "2" toNodeParameters
          "R" relationshipParameters
          }
         )
        fromNodeLabel
        (if (= fromNodeLabel "")
          ""
          (reduce #(str %1 ":" %2) "" fromNodeLabel)
          )
        toNodeLabel
        (if (= toNodeLabel "")
          ""
          (reduce #(str %1 ":" %2) "" toNodeLabel)
          )
        relationshipType
        (if (= relationshipType "")
          ""
          (str ":" relationshipType)
          )
        builtQuery
        {:query
         (if nodeInfo?
           (str "MATCH path=(n" fromNodeLabel " "
                ((combinedProperties :propertyStringMap) "1")
                ")-[p" relationshipType " "
                ((combinedProperties :propertyStringMap) "R")
                "]->(m" toNodeLabel " "
                ((combinedProperties :propertyStringMap) "2")
                ") RETURN " (if count?
                              "count(path)"
                              "path"))
           (str "MATCH (n" fromNodeLabel " "
                ((combinedProperties :propertyStringMap) "1")
                ")-[p" relationshipType " "
                ((combinedProperties :propertyStringMap) "R")
                "]->(m" toNodeLabel " "
                ((combinedProperties :propertyStringMap) "2")
                ") RETURN " (if count?
                              "count(p)"
                              "p"))
           )
         :parameters
         (combinedProperties :combinedPropertyMap)
         }
        ]
    (if execute?
      (if nodeInfo?
        (map #(first ((% (if count?
                           "count(path)"
                           "path")) :segments))
             (first ((gdriver/runQuery builtQuery) :results))) 
        (map #(% (if count?
                   "count(p)"
                   "p"))
             (first ((gdriver/runQuery builtQuery) :results)))
        )
      builtQuery
      )
    )
  )

(defn deleteRelations
  "Get relations matched by inNode/outNode/type and properties"
  [& {:keys [:fromNodeLabel
             :fromNodeParameters
             :relationshipType
             :relationshipParameters
             :toNodeLabel
             :toNodeParameters
             :execute?]
      :or {:execute? true
           :toNodeParameters {}
           :fromNodeParameters {}
           :relationshipParameters {}
           :fromNodeLabel ""
           :toNodeLabel ""
           :relationshipType ""}}]
  (let [combinedProperties
        (combinePropertyMap
         {"1" fromNodeParameters
          "2" toNodeParameters
          "R" relationshipParameters
          }
         )
        fromNodeLabel
        (if (= fromNodeLabel "")
          ""
          (reduce #(str %1 ":" %2) "" fromNodeLabel)
          )
        toNodeLabel
        (if (= toNodeLabel "")
          ""
          (reduce #(str %1 ":" %2) "" toNodeLabel)
          )
        relationshipType
        (if (= relationshipType "")
          ""
          (str ":" relationshipType)
          )
        builtQuery
        {:query
         (str "MATCH (n" fromNodeLabel " "
              ((combinedProperties :propertyStringMap) "1")
              ")-[p" relationshipType " "
              ((combinedProperties :propertyStringMap) "R")
              "]->(m" toNodeLabel " "
              ((combinedProperties :propertyStringMap) "2")
              ") delete p")
         :parameters
         (combinedProperties :combinedPropertyMap)
         }
        ]
    (if execute?
      ((gdriver/runQuery builtQuery) :summary)
      builtQuery
      )
    )
  )

(defn getNeighborhood
  "Get the neighborhood of a particular node"
  [& {:keys [:label
             :parameters]
      :or {:parameters {}}
      }
   ]
  (let [nodeseq (getNodes :label label
                          :parameters parameters)
        ]
    (if (not= (count nodeseq) 1)
      "Error"
      (let [nodeLabel ((first nodeseq) :labels)
            nodeParameters ((first nodeseq) :properties)
            ]
        {:labels nodeLabel
         :properties nodeParameters
         :outNodes (map #(select-keys % [:labels
                                         :properties
                                         :toNode])
                        (getRelations :fromNodeLabel nodeLabel
                                      :fromNodeParameters nodeParameters)
                        )
         :inNodes (map #(select-keys % [:labels
                                        :properties
                                        :fromNode])
                       (getRelations :toNodeLabel nodeLabel
                                     :toNodeParameters nodeParameters)
                       )
         }
        )
      )
    )
  )

(defn getInRels
  [& {:keys [:labels
             :inNodeParameters]
      :or {:inNodeParameters {}}}]
  {:pre [(coll? labels)
         (every? string? labels)]}
  (let [labelString (if (empty? labels)
                        ""
                        (str ":" (clojure.string/join ":" labels)))
        builtQuery {:query (str "MATCH (n"
                                labelString
                                " "
                                (createParameterPropertyString inNodeParameters)
                                " )<-[relation]-(node) RETURN relation,node.UUID"
                                )
                    :parameters inNodeParameters}]
    (map (fn [rel]  (dissoc (rel "relation") :fromNode :toNode)) (first ((gdriver/runQuery builtQuery) :results)))))

(defn getNBH
  "GET NBH"
  [& {:keys [:UUID]}]
  {:pre [(string? UUID)]}
  (let [nodeMatched (getNodeByUUID :UUID UUID)
        nodeNBH (if (nil? nodeMatched)
                  nil
                  (getInRels :labels (nodeMatched :labels)
                             :inNodeParameters {"UUID" UUID}))
        ]
    (if (nil? nodeMatched)
      nil
      {:node (assoc nodeMatched :labels (into #{} (nodeMatched :labels)))
       :inRelations (into #{} nodeNBH)}))
  )

;;Class building functions start here

(defn prepMapAsArg
  "Converts a map so that it can be used as keyArgs"
  [keyMap]
  (reduce #(concat %1 %2) keyMap))

(defn reduceQueryColl
  "Reduce collections/sub-collections of queries (({:query '...' :parameters {}})...()...()...) to a single collection of queries ({:query '...' :parameters {}} {...} {...})"
  [queryCollection]
  (reduce
   #(if
        (some map? %2)
      (concat %1 %2)
      (if (map? %2)
        (concat %1 [%2])
        (reduceQueryColl %1))
      )
   []
   queryCollection
   )
  )

(defn manageConstraints
  "Manage unique constraints or existance constraints.
  :label should be a string.
  :CD should be either of 'CREATE','DROP'
  :propertyVec should be a vector of properties(string).
  :constraintType should be either UNIQUE or NODEEXISTANCE or RELATIONEXISTANCE or NODEKEY.
  if :constraintType is NODEKEY, :propertyVec should be a vector of vectors of properties(string).
  :execute? (boolean) whether the constraints are to be created, or just return preparedQueries"
  [& {:keys [:label
             :CD
             :propertyVec
             :constraintType
             :execute?]
      :or {:execute? true}
      }
   ]
  {:pre [
         (string? label)
         (contains? #{"CREATE" "DROP"} CD)
         (not (empty? propertyVec))
         (contains?   #{"UNIQUE"
                        "NODEEXISTANCE"
                        "RELATIONEXISTANCE"
                        "NODEKEY"} constraintType)
         (if
             (= "NODEKEY" constraintType)
           (every? #(and (coll? %)
                         (not (empty? %))
                         )
                   propertyVec)
           true
           )
         ]
   }
  (let [queryBuilder (case constraintType
                       "UNIQUE" #(str "(label:" label
                                      ") ASSERT label." % " IS UNIQUE")
                       "NODEEXISTANCE" #(str "(label:" label
                                             ") ASSERT exists(label." % ")")
                       "RELATIONEXISTANCE" #(str "()-[label:" label
                                                 "]-() ASSERT exists(label." % ")")
                       "NODEKEY" #(str "(label:" label
                                       ") ASSERT (" (clojure.string/join
                                                     ", "
                                                     (map (fn [property]
                                                            (str "label." property)
                                                            )
                                                          %)
                                                     )
                                       ") IS NODE KEY"
                                       )
                       )
        builtQueries (map #(->
                            {:query
                             (str CD " CONSTRAINT ON " (queryBuilder %))
                             :parameters {}
                             }
                            ) propertyVec)
        ]
    (if
        execute?
      (apply gdriver/runQuery builtQueries)
      builtQueries)
    )
  )

(defn manageUniqueConstraints
  "Create/Drop Unique Constraints on label properties.
  :label is treated as a Node Label.
  :CD can be CREATE,DROP.
  :propertyVec should be a vector of properties"
  [& {:keys [:label
             :CD
             :propertyVec
             :execute?]
      :or {:execute? true}
      :as keyArgs}
   ]
  (apply manageConstraints
         (prepMapAsArg
          (assoc
           keyArgs
           :constraintType "UNIQUE"
           )
          )
         )
  )

(defn manageExistanceConstraints
  "Create/Drop Existance Constraints on label properties.
  :label is treated as a Node label or relation label based on value of NR.
  :CD can be CREATE, DROP.
  :propertyVec should be a vector of properties.
  :NR should be either NODE or RELATION"
  [& {:keys [:label
             :CD
             :propertyVec
             :NR
             :execute?]
      :or {:execute? true}
      :as keyArgs}]
  {:pre [(contains? #{"CREATE" "DROP"} CD)]
   }
  (apply manageConstraints
         (prepMapAsArg
          (assoc
           (dissoc
            keyArgs :NR)
           :constraintType (str NR "EXISTANCE")
           )
          )
         )
  )

(defn manageNodeKeyConstraints
  "Create/Drop Node Key Constraints on node label properties.
  :label is treated as node label.
  :CD can be CREATE, DROP.
  :propPropVec should be a vector of vectors of properties(string)."
  [& {:keys [:label
             :CD
             :propPropVec
             :execute?]
      :or {:execute? true}
      :as keyArgs}]
  ;;For some reason, creating/dropping a nodekey doesn't reflect on summary.
  ;;So don't be surprised if no errors occur or no changes exist in the summary.
  (apply manageConstraints
         (prepMapAsArg
          (assoc
           keyArgs
           :constraintType "NODEKEY"
           :propertyVec propPropVec)
          )
         )
  )

(defn createNCConstraints
  "Create Constraints that apply to nodes with label NeoConstraint"
  [& {:keys [:execute?] :or {:execute? true}}]
  (manageNodeKeyConstraints :label "NeoConstraint"
                            :CD "CREATE"
                            :propPropVec [["constraintType" "constraintTarget"]]
                            :execute? execute?)
  )

(defn createCATConstraints
  "Create Constraints that apply to relationships with label NeoConstraintAppliesTo"
  [& {:keys [:execute?]
      :or {:execute? true}}]
  (manageExistanceConstraints :label "NeoConstraintAppliesTo"
                              :CD "CREATE"
                              :propertyVec ["constraintValue"]
                              :NR "RELATION"
                              :execute? execute?)
  )

(defn createATConstraints
  "Creates Constraints that apply to nodes with label AttributeType"
  [& {:keys [:execute?]
      :or
      {:execute? true}}
   ]
  (manageNodeKeyConstraints :label "AttributeType"
                            :CD "CREATE"
                            :propPropVec [["_name"]]
                            :execute? execute?)
  )

(defn createCFConstraints
  "Creates Constraints that apply to nodes with label CustomFunction"
  [& {:keys [:execute?]
      :or {:execute? true}}
   ]
  (let [builtQueries (reduceQueryColl
                      [(manageNodeKeyConstraints :label "CustomFunction"
                                                 :CD "CREATE"
                                                 :propPropVec [["fnName"]]
                                                 :execute? false)
                       (manageExistanceConstraints :label "CustomFunction"
                                                   :CD "CREATE"
                                                   :propertyVec ["fnString" "fnIntegrity"]
                                                   :NR "NODE"
                                                   :execute? false)
                       ]
                      )
        ]
    (if
        execute?
      (apply gdriver/runQuery builtQueries)
      builtQueries)
    )
  )

(defn createVRATConstraints
  "Creates Constraints that apply to relations with label ValueRestrictionAppliesTo"
  [& {:keys [:execute?]
      :or {:execute? true}}
   ]
  (manageExistanceConstraints :label "ValueRestrictionAppliesTo"
                              :CD "CREATE"
                              :propertyVec ["constraintValue"]
                              :NR "RELATION"
                              :execute? execute?)
  )

(defn createCCATConstraints
  "Creates Constraints that apply to relations with label CustomConstraintAppliesTo"
  [& {:keys [:execute?]
      :or {:execute? true}}
   ]
  (manageExistanceConstraints :label "CustomConstraintAppliesTo"
                              :CD "CREATE"
                              :propertyVec ["constraintValue" "atList"]
                              :NR "RELATION"
                              :execute? execute?)
  )

(defn createClassConstraints
  "Create Constraints that apply to nodes with label Class"
  [& {:keys [:execute?]
      :or {:execute? true}}
   ]
  (let [builtQueries (reduceQueryColl
                      [(manageExistanceConstraints :label "Class"
                                                   :CD "CREATE"
                                                   :propertyVec ["isAbstract" "classType"]
                                                   :NR "NODE"
                                                   :execute? false)
                       (manageNodeKeyConstraints :label "Class"
                                                 :CD "CREATE"
                                                 :propPropVec [["className"]]
                                                 :execute? false)
                       ]
                      )
        ]
    (if
        execute?
      (apply gdriver/runQuery builtQueries)
      builtQueries)
    )
  )

(defn createNeoConstraint
  "Creates a NeoConstraint Node that describes a supported neo4j constraint.
  :constraintType should be either of UNIQUE,EXISTANCE,NODEKEY.
  :constraintTarget should be either of NODE,RELATION.
  If :constraintTarget is RELATION, then constraintType can only be EXISTANCE"
  [& {:keys [:constraintType
             :constraintTarget
             :execute?]
      :or {:execute? true}
      :as keyArgs}
   ]
  {:pre [
         (contains? #{"UNIQUE" "EXISTANCE" "NODEKEY"} constraintType)
         (contains? #{"NODE" "RELATION"} constraintTarget)
         (not
          (and
           (contains? #{"UNIQUE" "NODEKEY"} constraintType)
           (= "RELATION" constraintTarget)
           )
          )
         ]
   }
  (createNewNode :label "NeoConstraint"
                 :parameters {"constraintType" constraintType
                              "constraintTarget" constraintTarget}
                 :execute? execute?
                 )
  )

(defn createAllNeoConstraints
  "Creates all NeoConstraints"
  [& {:keys [:execute?]
      :or {:execute? true}}
   ]
  (let [builtQueries
        (map #(createNeoConstraint
               :constraintType (% 0)
               :constraintTarget (% 1)
               :execute? false)
             [
              ["UNIQUE" "NODE"]
              ["NODEKEY" "NODE"]
              ["EXISTANCE" "NODE"]
              ["EXISTANCE" "RELATION"]
              ]
             )
        ]
    (if
        execute?
      (apply gdriver/runQuery builtQueries)
      builtQueries))
  )

;;TODO CUSTOM CONSTRAINTS

(def validATDatatypes #{"java.lang.Boolean",
                        "java.lang.Byte",
                        "java.lang.Short",
                        "java.lang.Integer",
                        "java.lang.Long",
                        "java.lang.Float",
                        "java.lang.Double",
                        "java.lang.Character",
                        "java.lang.String",
                        "java.util.ArrayList"})

(def defaultDatatypeValues {"java.lang.Boolean" false
                            "java.lang.Byte" (byte 0)
                            "java.lang.Short" (short 0)
                            "java.lang.Integer" (int 0)
                            "java.lang.Long" (long 0)
                            "java.lang.Float" (float 0)
                            "java.lang.Double" (double 0)
                            "java.lang.Character" (char 0)
                            "java.lang.String" ""
                            "java.util.ArrayList" []})

(defn createCustomFunction
  "Creates a customFunction.
  :fnName should be string.
  :fnString should be string that represents CustomFunction template."
  [& {:keys [:fnName
             :fnString
             :execute?]
      :or {:execute? true}}
   ]
  {:pre [(gcust/stringIsCustFunction? fnString)
         (string? fnName)]}
  (createNewNode :label "CustomFunction"
                 :parameters {	"fnName" fnName
                              "fnString" fnString
                              "fnIntegrity" (gcust/hashCustomFunction fnString)}
                 :execute? execute?))

(defn getCustomFunctions
  "Get CustomFunctions"
  [& {:keys [:count?]
      :or {:count? false}}]
  (getNodes :label "CustomFunction"
            :parameters {}
            :count? count?))

(defn editCustomFunction
  "Edit a CustomFunction's fnName and/or fnString"
  [& {:keys [:fnName
             :changeMap
             :execute?]
      :or {:execute? true}}]
  {:pre [(string? fnName)
         (map? changeMap)
         (not (empty? changeMap))
         (clojure.set/subset? (into #{} (keys changeMap)) #{"fnName"
                                                            "fnString"}
                              )
         (or (and (contains? changeMap "fnString")
                  (gcust/stringIsCustFunction? (changeMap "fnString")
                                               )
                  )
             (not (contains? changeMap "fnString")))
         ]
   }
  (let [mChangeMap (merge changeMap (if (contains? changeMap "fnString")
                                      {"fnIntegrity" (gcust/hashCustomFunction (changeMap "fnString"))}
                                      {})
                          )]
    (editNodeProperties :label "CustomFunction"
                        :parameters {"fnName" fnName}
                        :changeMap mChangeMap
                        :execute? execute?)
    )
  )

(defn reHashCustomFunctions
  "Re-Hash all CustomFunctions.
  Could take a long time depending on size of database.
  Use only when customPassword is changed"
  [& {:keys [:execute?]
      :or {:execute? true}}]
  (let [builtQueries (map (fn [customFunction]
                            (let [cf (into {} (customFunction :properties))]
                              (editCustomFunction :fnName (cf "fnName")
                                                  :changeMap {"fnString" (cf "fnString")}
                                                  :execute? false
                                                  )
                              )
                            )
                          (getCustomFunctions)
                          )]
    (if execute?
      (apply gdriver/runQuery builtQueries)
      builtQueries)
    )
  )

(defn getClassAttributeTypes
  "Get all AttributeTypes 'attributed' to a class"
  [& {:keys [:className
             :count?]
      :or {:count? false}}]
  {:pre [(string? className)]}
  (map #((% (if count?
              "count(att)"
              "att")) :properties) (((gdriver/runQuery
                                      {:query (str "MATCH (class:Class {className:{className}})-[rel:HasAttributeType]->(att:AttributeType) RETURN "(if count?
                                                                                                                                                      "count(att)"
                                                                                                                                                      "att"))
                                       :parameters {"className" className}
                                       }
                                      ) :results) 0))
  )

(defn getClassApplicableSourceNT
  "Get all AttributeTypes 'attributed' to a class"
  [& {:keys [:className
             :count?]
      :or {:count? false}}]
  {:pre [(string? className)]}
  (map #((% (if count?
              "count(rl)"
              "rl")) :properties) (((gdriver/runQuery
                                     {:query (str "MATCH (class:Class {className:{className}})<-[rel:ApplicableSourceNT]-(rl:Class) RETURN "(if count?
                                                                                                                                              "count(rl)"
                                                                                                                                              "rl"))
                                      :parameters {"className" className}
                                      }
                                     ) :results) 0))
  )

(defn getClassApplicableTargetNT
  "Get all AttributeTypes 'attributed' to a class"
  [& {:keys [:className
             :count?]
      :or {:count? false}}]
  {:pre [(string? className)]}
  (map #((% (if count?
              "count(rl)"
              "rl")) :properties) (((gdriver/runQuery
                                     {:query (str "MATCH (class:Class {className:{className}})<-[rel:ApplicableTargetNT]-(rl:Class) RETURN "(if count?
                                                                                                                                              "count(rl)"
                                                                                                                                              "rl"))
                                      :parameters {"className" className}
                                      }
                                     ) :results) 0))
  )

(defn getClassNeoConstraints
  "Get all NeoConstraints attributed to a class"
  [& {:keys [:className
             :count?]
      :or {:count? false}}]
  {:pre [(string? className)]}
  (gdriver/runQuery
   {:query (str "MATCH (class:Class {className:{className}})<-[ncat:NeoConstraintAppliesTo]-(neo:NeoConstraint) RETURN " (if count?
                                                                                                                           "count(ncat)"
                                                                                                                           "ncat,neo"))
    :parameters {"className" className}
    }
   )
  )

(defn getClassCustomConstraints
  "Get all CustomConstraints applicable to a class"
  [& {:keys [:className
             :count?]
      :or {:count? false}}]
  {:pre [(string? className)]}
  (gdriver/runQuery
   {:query (str "MATCH (class:Class {className:{className}})<-[ccat:CustomConstraintAppliesTo]-(cf:CustomFunction) RETURN "(if count?
                                                                                                                             "count(ccat)"
                                                                                                                             "ccat,cf"))
    :parameters {"className" className}
    }
   )
  )

(defn getATValueRestrictions
  "Get all ValueRestriction applicable to an AttributeType with _name atname"
  [& {:keys [:atName
             :count?]
      :or {:count? false}}]
  {:pre [(string? atName)]}
  (gdriver/runQuery
   {:query (str "MATCH (at:AttributeType {_name:{atname}})<-[vr:ValueRestrictionAppliesTo]-(cf:CustomFunction) RETURN cf,vr"(if count?
                                                                                                                              "count(vr)"
                                                                                                                              "cf,vr"))
    :parameters {"atname" atName}
    }
   )
  )

(defn exemptClassNeoConstraint
  "Exempt a NeoConstraint that currently applies to a class.
  :className string
  :constraintType UNIQUE,NODEKEY,EXISTANCE
  :constraintTarget NODE,RELATION
  :constraintValue depends upon :_constraintTarget and :_constraintType"
  [& {:keys [:className
             :constraintType
             :constraintTarget
             :constraintValue
             :execute?]
      :or {:execute? true}
      :as keyArgs}
   ]
  {:pre [
         (contains? #{"UNIQUE" "NODEKEY" "EXISTANCE"} constraintType)
         (contains? #{"NODE" "RELATION"} constraintTarget)]}
  (manageConstraints :label className
                     :CD "DROP"
                     :propertyVec [(if
                                       (= "NODEKEY" constraintType)
                                     (into [] constraintValue)
                                     constraintValue
                                     )
                                   ]
                     :constraintType (case constraintType
                                       ("UNIQUE" "NODEKEY") constraintType
                                       "EXISTANCE" (str constraintTarget constraintType)
                                       )
                     :execute? execute?
                     )
  )

(defn applyClassNeoConstraint
  "Apply a NeoConstraint that apply to a class.
  :className string
  :constraintType UNIQUE,NODEKEY,EXISTANCE
  :constraintTarget NODE,RELATION
  :constraintValue depends upon :_constraintTarget and :_constraintType
  :execute?"
  [& {:keys [:className
             :constraintType
             :constraintTarget
             :constraintValue
             :execute?]
      :or {:execute? true}
      :as keyArgs}
   ]
  {:pre [
         (contains? #{"UNIQUE" "NODEKEY" "EXISTANCE"} constraintType)
         (contains? #{"NODE" "RELATION"} constraintTarget)]}
  (manageConstraints :label className
                     :CD "CREATE"
                     :propertyVec [(if
                                       (= "NODEKEY" constraintType)
                                     (into [] constraintValue)
                                     constraintValue
                                     )
                                   ]
                     :constraintType (case constraintType
                                       ("UNIQUE" "NODEKEY") constraintType
                                       "EXISTANCE" (str constraintTarget constraintType)
                                       )
                     :execute? execute?
                     )
  )

(defn exemptClassNeoConstraints
  "Exempt all NeoConstraints for a class"
  [& {:keys [:className
             :execute?]
      :or {:execute? true}
      :as keyArgs}]
  (let [builtQueries 
        (reduceQueryColl
         (map
          #(apply exemptClassNeoConstraint
                  (prepMapAsArg
                   (assoc
                    (clojure.set/rename-keys
                     (merge keyArgs
                            (into {} ((% "neo") :properties))
                            (into {} ((% "ncat") :properties))
                            )
                     {"constraintValue" :constraintValue
                      "constraintType" :constraintType
                      "constraintTarget" :constraintTarget
                      }
                     )
                    :execute? false
                    )
                   )
                  )
          (((getClassNeoConstraints :className className) :results) 0)
          )
         )
        ]
    (if
        execute?
      (apply gdriver/runQuery builtQueries)
      builtQueries)
    )
  )

(defn applyClassNeoConstraints
  "Apply all NeoConstraints for a class"
  [& {:keys [:className
             :execute?]
      :or {:execute? true}
      :as keyArgs}]
  (let [builtQueries 
        (reduceQueryColl
         (map
          #(apply applyClassNeoConstraint
                  (prepMapAsArg
                   (assoc
                    (clojure.set/rename-keys
                     (merge keyArgs
                            (into {} ((% "neo") :properties))
                            (into {} ((% "ncat") :properties))
                            )
                     {"constraintValue" :constraintValue
                      "constraintType" :constraintType
                      "constraintTarget" :constraintTarget
                      }
                     )
                    :execute? false
                    )
                   )
                  )
          (((getClassNeoConstraints :className className) :results) 0)
          )
         )
        ]
    (if
        execute?
      (apply gdriver/runQuery builtQueries)
      builtQueries)
    )
  )

(defn remRelApplicableType
  "Remove an applicable Source/Target type to a Relation Class, by removing a relation: ApplicableSourceNT/ApplicableTargetNT.
  :className should be className of relation class.
  :applicationType should be either SOURCE or TARGET as string.
  :applicableClassName should be a className of the source or target Node Class"
  [& {:keys [:className
             :applicationType
             :applicableClassName
             :execute?]
      :or {:execute? true}}
   ]
  {:pre [(string? className)
         (contains? #{"Source" "Target"} applicationType)
         (string? applicableClassName)
         (= 1 (count (getNodes :label "Class"
                               :parameters {"className" className
                                            "classType" "RELATION"}
                               )
                     )
            )
         (= 1 (count (getNodes :label "Class"
                               :parameters {"className" applicableClassName
                                            "classType" "NODE"}
                               )
                     )
            )
         ]
   }
  (let [builtQuery (deleteRelation :fromNodeLabel "Class"
                                   :fromNodeParameters {"className" className
                                                        "classType" "RELATION"}
                                   :relationshipType (str "Applicable"applicationType"NT")
                                   :relationshipParameters {}
                                   :toNodeLabel "Class"
                                   :toNodeParameters {"className" applicableClassName
                                                      "classType" "NODE"}
                                   :execute? false)
        ]
    (if
        execute?
      (gdriver/runQuery builtQuery)
      builtQuery)
    )
  )

(defn addRelApplicableType
  "Add an applicable Source/Target type to a Relation Class, by creating a relation: ApplicableSourceNT/ApplicableTargetNT.
  :className should be className of relation class.
  :applicationType should be either SOURCE or TARGET as string.
  :applicableClassName should be a className of the source or target Node Class"
  [& {:keys [:className
             :applicationType
             :applicableClassName
             :execute?]
      :or {:execute? true}}
   ]
  {:pre [(string? className)
         (contains? #{"Source" "Target"} applicationType)
         (string? applicableClassName)
         (= 1 (count (getNodes :label "Class"
                               :parameters {"className" className
                                            "classType" "RELATION"}
                               )
                     )
            )
         (= 1 (count (getNodes :label
                               "Class"
                               :parameters {"className" applicableClassName
                                            "classType" "NODE"}
                               )
                     )
            )
         ]
   }
  (let [builtQuery (createRelation :fromNodeLabel "Class"
                                   :fromNodeParameters {"className" className
                                                        "classType" "RELATION"}
                                   :relationshipType (str "Applicable"applicationType"NT")
                                   :relationshipParameters {}
                                   :toNodeLabel "Class"
                                   :toNodeParameters {"className" applicableClassName
                                                      "classType" "NODE"}
                                   :execute? false
                                   :unique? true)
        ]
    (if
        execute?
      (gdriver/runQuery builtQuery)
      builtQuery)
    )
  )

(defn getRelApplicableNTs
  "Get a relation class' ApplicableNTs.
  :className should be of a Class of classType 'RELATION'."
  [& {:keys [:className
             :count?]
      :or {:count? false}}]
  {:pre [(string? className)]}
  (let [combinedPropertyMap (combinePropertyMap {"RT" {"className" className
                                                       "classType" "RELATION"}
                                                 "NT" {"classType" "NODE"}}
                                                )
        builtQuery1 {:query (str "MATCH (rt:Class "
                                 ((combinedPropertyMap :propertyStringMap) "RT")
                                 ")-[:ApplicableSourceNT]->(nt:Class "
                                 ((combinedPropertyMap :propertyStringMap) "NT")
                                 ") RETURN "(if count?
                                              "count(nt)"
                                              "nt"))
                     :parameters (combinedPropertyMap :combinedPropertyMap)}
        builtQuery2 {:query (str "MATCH (rt:Class "
                                 ((combinedPropertyMap :propertyStringMap) "RT")
                                 ")-[:ApplicableTargetNT]->(nt:Class "
                                 ((combinedPropertyMap :propertyStringMap) "NT")
                                 ") RETURN "(if count?
                                              "count(nt)"
                                              "nt"))
                     :parameters (combinedPropertyMap :combinedPropertyMap)}
        ]
    ((gdriver/runQuery builtQuery1 builtQuery2) :results)
    )
  )

(defn addClassAT
  "Adds a relation HasAttributeType from Class to AttributeType.
  :_atname: _name of AttributeType.
  :_atdatatype: _datatype of AttributeType.
  :className: className of Class"
  [& {:keys [:_atname
             :className
             :execute?]
      :or {:execute? true}}
   ]
  {:pre [
         (string? className)
         (= 1 (count (getNodes :label "AttributeType"
                               :parameters {"_name" _atname}
                               ))
            )
         ]
   }
  (createRelation :fromNodeLabel "Class"
                  :fromNodeParameters {"className" className}
                  :relationshipType "HasAttributeType"
                  :relationshipParameters {}
                  :toNodeLabel "AttributeType"
                  :toNodeParameters {"_name" _atname}
                  :unique? true
                  :execute? execute?)
  )

(defn remClassAT
  "Removes relation HasAttributeType from Class to AttributeType.
  :_atname: _name of AttributeType.
  :_atdatatype: _datatype of AttributeType.
  :className: className of Class"
  [& {:keys [:_atname
             :className
             :execute?]
      :or {:execute? true}}
   ]
  {:pre [
         (string? className)
         (= 1 (count (getNodes :label "AttributeType"
                               :parameters {"_name" _atname}
                               ))
            )
         ]
   }
  (deleteRelation :fromNodeLabel "Class"
                  :fromNodeParameters {"className" className}
                  :relationshipType "HasAttributeType"
                  :relationshipParameters {}
                  :toNodeLabel "AttributeType"
                  :toNodeParameters {"_name" _atname}
                  :execute? execute?)
  )

(defn addATVR
  "Adds a ValueRestriction to an AttributeType.
  Creates a relation ValueRestrictionAppliesTo from CustomFunction to AttributeType.
  :_atname should be _name of an AttributeType.
  :fnName should be fnName of a CustomFunction.
  :constraintValue should be value to be passed as CustomFunction's second argument"
  [& {:keys [:_atname
             :fnName
             :constraintValue
             :execute?]
      :or {:execute? true}}
   ]
  {:pre [(string? _atname)
         (string? fnName)]}
  (createRelation :fromNodeLabel "CustomFunction"
                  :fromNodeParameters {"fnName" fnName}
                  :relationshipType "ValueRestrictionAppliesTo"
                  :relationshipParameters {"constraintValue" constraintValue}
                  :toNodeLabel "AttributeType"
                  :toNodeParameters {"_name" _atname}
                  :unique? true
                  :execute? execute?)
  )

(defn remATVR
  "Removes a ValueRestriction to an AttributeType.
  Creates a relation ValueRestrictionAppliesTo from CustomFunction to AttributeType.
  :_atname should be _name of an AttributeType.
  :fnName should be fnName of a CustomFunction.
  :constraintValue should be value to be passed as CustomFunction's second argument"
  [& {:keys [:_atname
             :fnName
             :constraintValue
             :execute?]
      :or {:execute? true}}
   ]
  {:pre [(string? _atname)
         (string? fnName)]}
  (deleteRelation :fromNodeLabel "CustomFunction"
                  :fromNodeParameters {"fnName" fnName}
                  :relationshipType "ValueRestrictionAppliesTo"
                  :relationshipParameters {"constraintValue" constraintValue}
                  :toNodeLabel "AttributeType"
                  :toNodeParameters {"_name" _atname}
                  :execute? execute?)
  )

(defn editATVR
  "Edits a ValueRestriction to an AttributeType.
  Creates a relation ValueRestrictionAppliesTo from CustomFunction to AttributeType.
  :_atname should be _name of an AttributeType.
  :fnName should be fnName of a CustomFunction.
  :constraintValue should be value to be passed as CustomFunction's second argument of existing ValueRestriction
  :newConstraintValue"
  [& {:keys [:_atname
             :fnName
             :constraintValue
             :newConstraintValue
             :execute?]
      :or {:execute? true}}
   ]
  {:pre [(string? _atname)
         (string? fnName)]}
  (editRelation :fromNodeLabel "CustomFunction"
                :fromNodeParameters {"fnName" fnName}
                :relationshipType "ValueRestrictionAppliesTo"
                :relationshipParameters {"constraintValue" constraintValue}
                :toNodeLabel "AttributeType"
                :toNodeParameters {"_name" _atname}
                :newRelationshipParameters {"constraintValue" newConstraintValue}
                :execute? execute?)
  )

(defn editClassNC
  "Edits relation NeoConstraintAppliesTo from a NeoConstraint to a Class.
  :className should be a string.
  :constraintType should be either of UNIQUE,EXISTANCE,NODEKEY
  :constraintTarget should be either of NODE,RELATION.
  :constraintValue should be _name of an  AttributeType or collection of _names, in case of NODEKEY.
  :newConstraintValue"
  [& {:keys [:constraintType
             :constraintTarget
             :constraintValue
             :newConstraintValue
             :className
             :execute?]
      :or {:execute? true}}
   ]
  {:pre [(string? className)
         (= 1 (count (getNodes :label "Class"
                               :parameters {"className" className
                                            "classType" constraintTarget}
                               )
                     )
            )
         (or (and (contains? #{"UNIQUE" "EXISTANCE"} constraintType) (string? newConstraintValue))
             (and (= constraintType "NODEKEY") (coll? newConstraintValue) (every? string? newConstraintValue)))
         ]
   }
  (let [builtQueries [[(editRelation :fromNodeLabel "NeoConstraint"
                                     :fromNodeParameters {"constraintType" constraintType
                                                          "constraintTarget" constraintTarget}
                                     :relationshipType "NeoConstraintAppliesTo"
                                     :relationshipParameters {"constraintValue" constraintValue}
                                     :toNodeLabel "Class"
                                     :toNodeParameters {"className" className
                                                        "classType" constraintTarget}
                                     :newRelationshipParameters {"constraintValue" newConstraintValue}
                                     :execute? false)
                       ]
                      (reduceQueryColl [(exemptClassNeoConstraint :className className
                                                                  :constraintType constraintType
                                                                  :constraintTarget constraintTarget
                                                                  :constraintValue constraintValue
                                                                  :execute? false)
                                        (applyClassNeoConstraint :className className
                                                                 :constraintType constraintType
                                                                 :constraintTarget constraintTarget
                                                                 :constraintValue newConstraintValue
                                                                 :execute? false
                                                                 )
                                        ]
                                       )
                      ]
        ]
    (if
        execute?
      (apply gdriver/runTransactions builtQueries)
      builtQueries))
  )

(defn getNeoConstraintsWithAT
  "Get NeoConstraint that are applied with a particular AttributeType"
  [& {:keys [:atName
             :count?]
      :or {:count? false}}]
  {:pre [(string? atName)]}
  (apply concat ((gdriver/runQuery {:query (str "MATCH (neo:NeoConstraint {constraintType:\"NODEKEY\"})-[rel:NeoConstraintAppliesTo]->(cl:Class)"
                                                " WHERE {ATT} IN rel.constraintValue"
                                                " RETURN "
                                                (if count?
                                                  "count(neo)"
                                                  "cl.className,neo.constraintType,neo.constraintTarget,rel.constraintValue")
                                                )
                                    :parameters {"ATT" atName}}
                                   {:query (str "MATCH (neo:NeoConstraint)-[rel:NeoConstraintAppliesTo]->(cl:Class)"
                                                " WHERE {ATT} IN rel.constraintValue and"
                                                " neo.constraintType IN [\"UNIQUE\",\"EXISTANCE\"]"
                                                " RETURN "
                                                (if count?
                                                  "count(neo)"
                                                  "cl.className,neo.constraintType,neo.constraintTarget,rel.constraintValue"))
                                    :parameters {"ATT" atName}}) :results)))

(defn createDelATNC
  "Creates a query to remove an AttributeType in all relations with label NeoConstraintAppliesTo.
  :atName should be a string, _name of an AttributeType."
  [& {:keys [:atName]}
   ]
  {:pre [string? atName]}
  (let [propertyMap {"ATT" atName}]
    [{:query (str "MATCH (neo1:NeoConstraint {constraintType:\"NODEKEY\"})-[rel1:NeoConstraintAppliesTo]->(cl1:Class)"
                  " "(createPropListEditString :varName "rel1"
                                               :propName "constraintValue"
                                               :editType "DELETE"
                                               :editVal "ATT"
                                               :withWhere? false)
                  " DELETE rel1")
      :parameters propertyMap}
     {:query (str "MATCH"
                  " (neo2:NeoConstraint)-[rel2:NeoConstraintAppliesTo]->(cl2:Class)"
                  " WHERE neo2.constraintType IN [\"UNIQUE\",\"EXISTANCE\"]"
                  " AND {ATT} IN rel2.constraintValue"
                  " DELETE rel2")
      :parameters propertyMap}])
  )

(defn createReplaceATNC
  "Creates a query to replace an AttributeType in all relations with label NeoConstraintAppliesTo.
  :atName should be a string, _name of an AttributeType.
  :renameName should be a string, replacement _name"
  [& {:keys [:atName
             :renameName]}]
  {:pre [(string? atName)
         (string? renameName)]}
  (let [propertyMap {"ATT" atName "att" renameName}]
    {:query (str "MATCH (neo1:NeoConstraint {constraintType:\"NODEKEY\"})-[rel1:NeoConstraintAppliesTo]->(cl1:Class),"
                 " (neo2:NeoConstraint)-[rel2:NeoConstraintAppliesTo]->(cl2:Class)"
                 " WHERE neo2.constraintType IN [\"UNIQUE\",\"EXISTANCE\"]"
                 " AND {ATT} IN rel1.constraintValue"
                 " AND {ATT} IN rel2.constraintValue"
                 " "(createPropListEditString :varName "rel1"
                                              :propName "constraintValue"
                                              :editType "REPLACE"
                                              :editVal "ATT"
                                              :replaceVal "att"
                                              :withWhere? false)","
                 " rel2.constraintValue={att}"
                 " RETURN rel1,rel2")
     :parameters propertyMap})
  )

(defn remClassNC
  "Removes relation NeoConstraintAppliesTo from a NeoConstraint to a Class.
  :constraintType should be either of UNIQUE,EXISTANCE,NODEKEY.
  :constraintTarget should be either of NODE,RELATION.
  :constraintValue should be _name of an  AttributeType or collection of _names, in case of NODEKEY"
  [& {:keys [:constraintType
             :constraintTarget
             :constraintValue
             :className
             :execute?]
      :or {:execute? true}}
   ]
  {:pre [
         (string? className)
         (= 1 (count (getNodes :label "Class"
                               :parameters {"className" className
                                            "classType" constraintTarget}
                               )
                     )
            )
         ]
   }
  (let [builtQueries [[(deleteRelation :fromNodeLabel "NeoConstraint"
                                       :fromNodeParameters {"constraintType" constraintType
                                                            "constraintTarget" constraintTarget}
                                       :relationshipType "NeoConstraintAppliesTo"
                                       :relationshipParameters {"constraintValue" constraintValue}
                                       :toNodeLabel "Class"
                                       :toNodeParameters {"className" className}
                                       :execute? false)]
                      (exemptClassNeoConstraint :className className
                                                :constraintType constraintType
                                                :constraintTarget constraintTarget
                                                :constraintValue constraintValue
                                                :execute? false)
                      ]
        ]
    (if
        execute?
      (apply gdriver/runTransactions builtQueries)
      builtQueries)
    )
  )

(defn addClassNC
  "Adds a relation NeoConstraintAppliesTo from NeoConstraint to Class.
  :constraintType should be either of UNIQUE,EXISTANCE,NODEKEY.
  :constraintTarget should be either of NODE,RELATION.
  :constraintValue should be _name of an  AttributeType or collection of _names, in case of NODEKEY"
  [& {:keys [:constraintType
             :constraintTarget
             :constraintValue
             :className
             :execute?]
      :or {:execute? true}}
   ]
  {:pre [(string? className)
         (= 1 (count (getNodes :label "Class"
                               :parameters {"className" className
                                            "classType" constraintTarget}
                               )
                     )
            )
         (or (and (contains? #{"UNIQUE" "EXISTANCE"} constraintType) (string? constraintValue))
             (and (= constraintType "NODEKEY") (coll? constraintValue) (every? string? constraintValue)))
         ]
   }
  (let 	[createRelationQuery
         (createRelation :fromNodeLabel "NeoConstraint"
                         :fromNodeParameters {"constraintType" constraintType
                                              "constraintTarget" constraintTarget}
                         :relationshipType "NeoConstraintAppliesTo"
                         :relationshipParameters {"constraintValue" constraintValue}
                         :toNodeLabel "Class"
                         :toNodeParameters {"className" className}
                         :execute? false)
         applyClassNeoConstraintQuery (applyClassNeoConstraint 	:className className
                                                                :constraintType constraintType 
                                                                :constraintTarget constraintTarget 
                                                                :constraintValue constraintValue 
                                                                :execute? false)
         combinedQuery (vec (conj applyClassNeoConstraintQuery
                                  createRelationQuery)
                            )
         ]
    (if
        execute?
      (gdriver/runTransactions (conj [] createRelationQuery)
                               (vec applyClassNeoConstraintQuery)
                               )
      combinedQuery
      )		  	
    )
  )

(defn addClassCC
  "Adds a relation CustomConstraintAppliesTo from CustomFunction to Class.
  :fnName of a CustomFunction.
  :atList should be list of AttributeTypes' _name.
  :constraintValue should be value to be passed as CustomFunction's second argument"
  [& {:keys [:fnName
             :atList
             :constraintValue
             :className
             :execute?]
      :or {:execute? true}}
   ]
  {:pre [(string? className)
         (string? fnName)
         (coll? atList)
         (every? string? atList)]}
  (let [classAttributeTypes (getClassAttributeTypes :className className)]
    (if (not (every? #(= 1 (count (filter
                                   (fn
                                     [at]
                                     (= % ((into {} at) "_name")
                                        )
                                     ) classAttributeTypes)
                                  )
                         ) atList)
             )
      (throw (Exception. (str "atList must contain _name's of an AttributeType :" atList))))
    (createRelation :fromNodeLabel "CustomFunction"
                    :fromNodeParameters {"fnName" fnName}
                    :relationshipType "CustomConstraintAppliesTo"
                    :relationshipParameters {"atList" atList
                                             "constraintValue" constraintValue}
                    :toNodeLabel "Class"
                    :toNodeParameters {"className" className}
                    :execute? execute?)
    )
  )

(defn remClassCC
  "Delete relation CustomConstraintAppliesTo from CustomFunction to Class.
  :fnName of a CustomFunction.
  :atList should be list of AttributeTypes' _name.
  :constraintValue should be value to be passed as CustomFunction's second argument"
  [& {:keys [:fnName
             :atList
             :constraintValue
             :className
             :execute?]
      :or {:execute? true}}
   ]
  {:pre [(string? className)
         (string? fnName)
         (coll? atList)
         (every? string? atList)]}
  (deleteRelation :fromNodeLabel "CustomFunction"
                  :fromNodeParameters {"fnName" fnName}
                  :relationshipType "CustomConstraintAppliesTo"
                  :relationshipParameters {"atList" atList
                                           "constraintValue" constraintValue}
                  :toNodeLabel "Class"
                  :toNodeParameters {"className" className}
                  :execute? execute?)
  )

(defn editClassCC
  "Edit relation CustomConstraintAppliesTo from CustomFunction to Class.
  :fnName of a CustomFunction.
  :className of Class
  :atList should be list of AttributeTypes' _name of existing CCAT relation.
  :constraintValue should be value to be passed as CustomFunction's second argument of existing CCAT relation.
  :editMap map with keys 'atList' and 'constraintValue' and appropriate values"
  [& {:keys [:fnName
             :atList
             :constraintValue
             :className
             :editMap
             :execute?]
      :or {:execute? true}}
   ]
  {:pre [(string? className)
         (string? fnName)
         (coll? atList)
         (every? string? atList)
         (map? editMap)
         (clojure.set/subset? (keys editMap) #{"atList" "constraintValue"})
         (or (coll? (editMap "atList")) (nil? (editMap "atList")))]}
  (editRelation :fromNodeLabel "CustomFunction"
                :fromNodeParameters {"fnName" fnName}
                :relationshipType "CustomConstraintAppliesTo"
                :relationshipParameters {"atList" atList
                                         "constraintValue" constraintValue}
                :toNodeLabel "Class"
                :toNodeParameters {"className" className}
                :newRelationshipParameters editMap
                :execute? execute?)
  )

(defn createDelATCC
  "Creates a query to remove an AttributeType in all relations with label CustomConstraintAppliesTo.
  :atName should be a string, _name of an AttributeType."
  [& {:keys [:atName]}
   ]
  {:pre [string? atName]}
  (let [propertyMap {"ATT" atName}]
    {:query (str "MATCH (cc:CustomFunction)-[rel:CustomConstraintAppliesTo]->(cl:Class)"
                 " WHERE {ATT} IN rel.atList"
                 " "(createPropListEditString :varName "rel"
                                              :propName "atList"
                                              :editType "DELETE"
                                              :editVal "ATT"
                                              :replaceVal "att"
                                              :withWhere? false)
                 )
     :parameters propertyMap})
  )

(defn createReplaceATCC
  "Creates a query to replace an AttributeType in all relations with label CustomConstraintAppliesTo.
  :atName should be a string, _name of an AttributeType.
  :renameName should be a string, replacement _name"
  [& {:keys [:atName
             :renameName]}]
  {:pre [(string? atName)
         (string? renameName)]}
  (let [propertyMap {"ATT" atName "att" renameName}]
    {:query (str "MATCH (cc:CustomFunction)-[rel:CustomConstraintAppliesTo]->(cl:Class)"
                 " WHERE {ATT} IN rel.atList"
                 " "(createPropListEditString :varName "rel"
                                              :propName "atList"
                                              :editType "REPLACE"
                                              :editVal "ATT"
                                              :replaceVal "att"
                                              :withWhere? false)
                 )
     :parameters propertyMap})
  )

(defn addSubTypeVRQueryVec
  "Returns a vector of queries consisting of the queries 
  for adding superclass NeoConstraints to the subclass"
  [& {:keys [:_name
             :_datatype
             :subTypeOf]}]
  (let
      [[superTypeName] subTypeOf
       superTypeVRVec (vec (((getATValueRestrictions :atName (str superTypeName)) :results) 0))
       is_aRelationQuery (createRelation 	
                          :fromNodeLabel "AttributeType"
                          :fromNodeParameters {"_name" _name}
                          :relationshipType "is_a"
                          :relationshipParameters {}
                          :toNodeLabel "AttributeType"
                          :toNodeParameters {"_name" superTypeName}
                          :execute? false
                          )
       VRQueriesVec (into [is_aRelationQuery]
                          (map (fn
                                 [SingleVRMap]
                                 (addATVR :_atname _name
                                          :fnName (((SingleVRMap "cf") :properties) "fnName")
                                          :constraintValue (((SingleVRMap "vr") :properties) "constraintValue")
                                          :execute? false)
                                 )
                               superTypeVRVec
                               )
                          )]
    VRQueriesVec	
    )
  )

(defn createAttributeType
  "Creates a node with Label AttributeType.
  :subTypeOf should be a vector containing the name of the superType if any
  :_name should be a string
  :_datatype should be a string of one of the following: 'java.lang.Boolean', 'java.lang.Byte', 'java.lang.Short', 'java.lang.Integer', 'java.lang.Long', 'java.lang.Float', 'java.lang.Double', 'java.lang.Character', 'java.lang.String', 'java.util.ArrayList'.
  :subjectQualifier should be a list of strings.
  :attributeQualifier should be a list of strings.
  :valueQualifier should be a list of strings"
  [& {:keys [:_name
             :_datatype
             :subTypeOf
             :subjectQualifier
             :attributeQualifier
             :valueQualifier
             :execute?
             :subTypeOf]
      :or {:execute? true
           :subjectQualifier []
           :attributeQualifier []
           :valueQualifier []
           :subTypeOf []}
      :as keyArgs}]
  {:pre [
         (string? _name)
         (contains? validATDatatypes _datatype)
         ]
   }
  (let [createNewNodeQuery 
        (createNewNode :label "AttributeType"
                       :parameters {"_name" _name
                                    "_datatype" _datatype
                                    "subjectQualifier" subjectQualifier
                                    "attributeQualifier" attributeQualifier
                                    "valueQualifier" valueQualifier}
                       :execute? false)]
    (if (not (empty? subTypeOf))
                                        ;"Adds the attributes,NeoConstraints and CustomConstraints of the superclass to the subclass"
      (let
          [completeQueryVec (vec
                             (concat [createNewNodeQuery]
                                     (addSubTypeVRQueryVec :_name _name
                                                           :_datatype _datatype
                                                           :subTypeOf subTypeOf)
                                     )
                             )
           [superTypeName] subTypeOf]
        (if (not (empty? (getNodes :label "AttributeType"
                                   :parameters {"_name" (str superTypeName)}
                                   :execute? true)
                         )
                 )
          (if
              execute?
            (apply gdriver/runQuery completeQueryVec)
            completeQueryVec	
            )
          (gdriver/runQuery)
          )
        )
      (if
          execute?
        (gdriver/runQuery createNewNodeQuery)
        createNewNodeQuery
        )
      )
    )
  )

(defn getATClasses
  "Get classes that have a particular attributeType.
  :_name should be a string, name of an AttributeType."
  [& {:keys [:_name
             :count?]
      :or {:count? false}}]
  {:pre [(string? _name)]}
  (((gdriver/runQuery {:query (str "MATCH (att:AttributeType {_name:{_name}})<-[:HasAttributeType]-(n:Class) RETURN "(if count?
                                                                                                                       "count(n)"
                                                                                                                       "n"))
                       :parameters {"_name" _name}}) :results) 0)
  )

(defn getAttributeTypes
  "Fetches all AttributeTypes from db"
  [& {:keys [:count?]
      :or {:count? false}}]
  (getNodes :label "AttributeType" :count? count?))

(defn editAttributeType
  "Edit an attributeType.
  `:editChanges` should be a map with at least one of the following keys :
  -`_name` should be a string
  -`_datatype` should be a string of one of the following: 'java.lang.Boolean', 'java.lang.Byte', 'java.lang.Short', 'java.lang.Integer', 'java.lang.Long', 'java.lang.Float', 'java.lang.Double', 'java.lang.Character', 'java.lang.String', 'java.util.ArrayList'.
  -`subjectQualifier` should be a list of strings.
  -`attributeQualifier` should be a list of strings.
  -`valueQualifier` should be a list of strings.
  CAREFUL when using forceMigrate... it will edit ALL nodes/relations with this attributeType, and automatically DROP and re-CREATE all constraints with the particular AttributeType.
  Currently will not check if there is actually a change in the AttributeType.
  If _datatype is a key in `:editChanges`, it will change the corresponding value in class instances to the default value, even though _datatype is the same.
  Using `:forceMigrate?` in editAttributeType is not advised when there are too many instances and constraints with the attributeType ... It would take WAYYY too much time.
  Also, when changing _datatype, if the AttributeType has some unique/nodekey constraint, the instances of the AT will not be edited, as the default values are non-unique.
  But, if _name and _datatype is being changed together, the instances will be updated, but the new constraints will not be created.
  One must manually edit all the instances to fit the constraints and then call `applyClassNeoConstraints` with the approproate `:className`"
  [& {:keys [:_name
             :editChanges
             :forceMigrate?
             :execute?]
      :or {:forceMigrate? false
           :execute? true}}
   ]
  {:pre [(string? _name)
         (map? editChanges)
         (clojure.set/subset? (into #{} (keys editChanges))
                              #{"_name"
                                "_datatype"
                                "subjectQualifier"
                                "attributeQualifier"
                                "valueQualifier"})
         (or (not (contains? editChanges "_name")) (and (contains? editChanges "_name") (string? (editChanges "_name"))))
         (or (not (contains? editChanges "_datatype")) (and (contains? editChanges "_datatype") (contains? validATDatatypes (editChanges "_datatype"))))
         ]
   }
  (let [editQuery (editNodeProperties :label "AttributeType"
                                      :parameters {"_name" _name}
                                      :changeMap editChanges
                                      :execute? false)
        ATClasses (getATClasses :_name _name)
        ATClassNames (map #(((% "n") :properties) "className") ATClasses)]
    (if (or (empty? ATClasses)
            (clojure.set/subset?
             (into #{} (keys editChanges))
             #{"subjectQualifier"
               "attributeQualifier"
               "valueQualifier"}
             )
            )
      (if execute?
        (gdriver/runQuery editQuery)
        editQuery)
      (if
          (not forceMigrate?)
        (throw (Exception. (str "Class(es) "(seq
                                             (map #((into {} ((% "n") :properties))
                                                    "className")
                                                  ATClasses)
                                             )
                                " have `"_name"`, use `:forceMigrate?` true to make functional changes to the class and it's instances automatically.")
                           )
               )
        (let [nameChangeQueries (if (contains? editChanges "_name")
                                  (let [neoConstraintsWithAT (getNeoConstraintsWithAT :atName _name)
                                        constraintDropQueries 
                                        (reduceQueryColl (map #(exemptClassNeoConstraint :execute? false
                                                                                         :className (% "cl.className")
                                                                                         :constraintTarget (% "neo.constraintTarget")
                                                                                         :constraintValue (% "rel.constraintValue")
                                                                                         :constraintType (% "neo.constraintType")) neoConstraintsWithAT))
                                        constraintCreateQueries (reduceQueryColl (map #(applyClassNeoConstraint :execute? false
                                                                                                                :className (% "cl.className")
                                                                                                                :constraintTarget (% "neo.constraintTarget")
                                                                                                                :constraintValue (if (= (type (% "rel.constraintValue")) java.util.Collections$UnmodifiableRandomAccessList)
                                                                                                                                   (editCollection :coll (into [] (% "rel.constraintValue"))
                                                                                                                                                   :editType "REPLACE"
                                                                                                                                                   :editVal _name
                                                                                                                                                   :replaceVal (editChanges "_name"))
                                                                                                                                   (editChanges "_name"))
                                                                                                                :constraintType (% "neo.constraintType")) neoConstraintsWithAT))
                                        dataEditQueries   (reduceQueryColl [(createReplaceATNC :atName _name
                                                                                               :renameName (editChanges "_name"))
                                                                            (createReplaceATCC :atName _name
                                                                                               :renameName (editChanges "_name"))
                                                                            {:query (str "MATCH (node) WHERE "
                                                                                         (clojure.string/join " or " (map #(str "node:" %) ATClassNames))
                                                                                         " AND "(createRenameString :addWhere? false
                                                                                                                    :varName "node"
                                                                                                                    :renameMap {_name (editChanges "_name")}))
                                                                             :parameters {}}])
                                        ]
                                    {:constraintDropQueries constraintDropQueries
                                     :constraintCreateQueries constraintCreateQueries
                                     :dataEditQueries dataEditQueries})
                                  {:constraintDropQueries []
                                   :constraintCreateQueries []
                                   :dataEditQueries []}
                                  )
              datatypeChangeQueries (if (contains? editChanges "_datatype")
                                      (let [dataEditQueries [{:query (str "MATCH (node) WHERE "
                                                                          (clojure.string/join " or " (map #(str "node:" %) ATClassNames))
                                                                          " SET node."(or (editChanges "_name") _name)" = {defVal}")
                                                              :parameters {"defVal" (defaultDatatypeValues (editChanges "_datatype"))}}]]
                                        {:constraintDropQueries []
                                         :constraintCreateQueries []
                                         :dataEditQueries dataEditQueries})
                                      {:constraintDropQueries []
                                       :constraintCreateQueries []
                                       :dataEditQueries []})]
          (let [constraintDropQueries (concat (nameChangeQueries :constraintDropQueries)
                                              (datatypeChangeQueries :constraintDropQueries))
                dataEditQueries (conj (concat (nameChangeQueries :dataEditQueries)
                                              (datatypeChangeQueries :dataEditQueries))
                                      editQuery)
                constraintCreateQueries (concat (nameChangeQueries :constraintCreateQueries)
                                                (datatypeChangeQueries :constraintCreateQueries))]
            (if
                execute?
              ;; (apply gdriver/runQuery constraintDropQueries)
              (gdriver/runTransactions constraintDropQueries dataEditQueries constraintCreateQueries)
              {:constraintDropQueries constraintDropQueries
               :dataEditQueries dataEditQueries
               :constraintCreateQueries constraintCreateQueries}))
          )
        )
      )
    )
  )
(defn deleteAttributeType
  "Delete an AttributeType.
  :forceMigrate?"
  [&{:keys [:_name
            :forceMigrate?
            :execute?]
     :or {:execute? false}}]
  {:pre [(string? _name)]}
  (let [deleteQuery (deleteDetachNodes :label "AttributeType"
                                       :parameters {"_name" _name}
                                       :execute? false)
        ATClasses (getATClasses :_name _name)
        ATClassNames (map #(((% "n") :properties) "className") ATClasses)]
    (if (empty? ATClasses)
      (if execute?
        (gdriver/runQuery deleteQuery)
        deleteQuery)
      (if
          (not forceMigrate?)
        (throw (Exception. (str "Class(es) "(seq
                                             (map #((into {} ((% "n") :properties))
                                                    "className")
                                                  ATClasses)
                                             )
                                " have `"_name"`, use `:forceMigrate?` true to make functional changes to the class and it's instances automatically.")
                           )
               )
        (let [neoConstraintsWithAT (getNeoConstraintsWithAT :atName _name)
              constraintDropQueries (reduceQueryColl (map #(exemptClassNeoConstraint :execute? false
                                                                                     :className (% "cl.className")
                                                                                     :constraintTarget (% "neo.constraintTarget")
                                                                                     :constraintValue (% "rel.constraintValue")
                                                                                     :constraintType (% "neo.constraintType")) neoConstraintsWithAT))
              constraintCreateQueries (reduceQueryColl (map #(if (= "NODEKEY" (% "neo.constraintType"))
                                                               (applyClassNeoConstraint :execute? false
                                                                                        :className (% "cl.className")
                                                                                        :constraintTarget (% "neo.constraintTarget")
                                                                                        :constraintValue (editCollection :coll (into [] (% "rel.constraintValue"))
                                                                                                                         :editType "DELETE"
                                                                                                                         :editVal _name)
                                                                                        :constraintType (% "neo.constraintType"))) neoConstraintsWithAT))
              dataEditQueries (reduceQueryColl [deleteQuery
                                                (createDelATNC :atName _name)
                                                (createDelATCC :atName _name)
                                                {:query (str "MATCH (node) WHERE "
                                                             (clojure.string/join " or " (map #(str "node:" %) ATClassNames))
                                                             " "(createRemString :varName "node"
                                                                                 :remPropertyList [_name]))
                                                 :parameters {}}])]
          (if
              execute?
            (gdriver/runTransactions constraintDropQueries dataEditQueries constraintCreateQueries)
            {:constraintDropQueries constraintDropQueries
             :dataEditQueries dataEditQueries
             :constraintCreateQueries constraintCreateQueries}
            )
          )
        )
      )
    )
  )

(defn addSubClassATQueryVec
  "Returns a vector of queries consisting of is_aRelationship 
  query and the queries for adding superclass AttributeTypes to the subclass"
  [& {:keys [:className
             :subClassOf]}] 
  (let [[superClassName] subClassOf
        superClassATVec (vec (getClassAttributeTypes :className (str superClassName))) 
        is_aRelationQuery 	
        (createRelation 	
         :fromNodeLabel "Class"
         :fromNodeParameters {"className" className}
         :relationshipType "is_a"
         :relationshipParameters {}
         :toNodeLabel "Class"
         :toNodeParameters {"className" superClassName}
         :execute? false
         )
        queriesVec
        (into [is_aRelationQuery]
              (map (fn
                     [SingleATMap] 
                     (addClassAT :_atname (SingleATMap "_name")
                                 :className className
                                 :execute? false)
                     )
                   superClassATVec
                   )
              )]
    queriesVec	
    )
  )

(defn addClassNCQuery
  "Returns addClassNC query without doing a check on the existence and the uniqueness of the class.
  :constraintType should be either of UNIQUE,EXISTANCE,NODEKEY.
  :constraintTarget should be either of NODE,RELATION.
  :constraintValue should be _name of an  AttributeType or collection of _names, in case of NODEKEY"
  [& {:keys [:constraintType
             :constraintTarget
             :constraintValue
             :className]}]
  (let 	[createRelationQuery (createRelation :fromNodeLabel "NeoConstraint"
                                             :fromNodeParameters {"constraintType" constraintType
                                                                  "constraintTarget" constraintTarget}
                                             :relationshipType "NeoConstraintAppliesTo"
                                             :relationshipParameters {"constraintValue" constraintValue}
                                             :toNodeLabel "Class"
                                             :toNodeParameters {"className" className}
                                             :execute? false)
         applyClassNeoConstraintQuery (applyClassNeoConstraint 	:className className 
                                                                :constraintType constraintType 
                                                                :constraintTarget constraintTarget 
                                                                :constraintValue constraintValue 
                                                                :execute? false)
         combinedQuery (conj (conj []
                                   (vec applyClassNeoConstraintQuery)
                                   )
                             (conj [] createRelationQuery)
                             )
         ]
    combinedQuery
    )
  )

;;;;;;Try to improve efficiency
(defn addSubClassNCQueryVec
  "Returns a vector of queries consisting of the queries 
  for adding superclass NeoConstraints to the subclass"
  [& {:keys [:className
             :subClassOf
             :classType]}
   ]
  (let
      [[superClassName] subClassOf
       superClassNCVec (vec (((getClassNeoConstraints :className (str superClassName)) :results) 0))
       addClassNCQueryVec (vec (into []
                                     (map (fn
                                            [SingleNCMap]
                                            (let [[Query1Vec Query2Vec] (addClassNCQuery :constraintType (((SingleNCMap "neo") :properties) "constraintType")
                                                                                         :constraintTarget classType
                                                                                         :constraintValue (((SingleNCMap "ncat") :properties) "constraintValue")
                                                                                         :className className
                                                                                         :execute? false)
                                                  ]
                                              Query1Vec
                                              )
                                            )
                                          superClassNCVec
                                          )
                                     )	
                               )
       completeNCQueryVec
       (vec 	(into addClassNCQueryVec 
                      (
                       map (fn
                             [SingleNCMap]
                             (let [[Query1Vec Query2Vec] (addClassNCQuery :constraintType (((SingleNCMap "neo") :properties) "constraintType")
                                                                          :constraintTarget classType
                                                                          :constraintValue (((SingleNCMap "ncat") :properties) "constraintValue")
                                                                          :className className
                                                                          :execute? false)
                                   ]
                               Query2Vec
                               )
                             )
                       superClassNCVec
                       )
                      )	
                )	
       ]
    completeNCQueryVec	
    )
  )

(defn addClassCCQuery
  "Returns query for addClassCC without doing a check on atList
  :fnName of a CustomFunction.
  :constraintValue should be value to be passed as CustomFunction's second argument"
  [& {:keys [:fnName
             :atList
             :constraintValue
             :className]}]
  {:pre [(string? className)
         (string? fnName)]}
  (createRelation 
   :fromNodeLabel "CustomFunction"
   :fromNodeParameters {"fnName" fnName}
   :relationshipType "CustomConstraintAppliesTo"
   :relationshipParameters {"atList" atList
                            "constraintValue" constraintValue}
   :toNodeLabel "Class"
   :toNodeParameters {"className" className}
   :execute? false
   )
  )

(defn addSubClassCCQueryVec
  "Returns a vector of queries consisting of the queries 
  for adding superclass CustomConstraints to the subclass"
  [& {:keys [:className
             :subClassOf]}
   ]
  (let
      [[superClassName] subClassOf
       superClassCCVec (vec (((getClassCustomConstraints :className (str superClassName)) :results) 0))
       CCQueriesVec (into []
                          (map (fn
                                 [SingleCCMap]
                                 (addClassCCQuery :fnName (((SingleCCMap "cf") :properties) "fnName")
                                                  :atList (vec (((SingleCCMap "ccat") :properties) "atList"))
                                                  :constraintValue (((SingleCCMap "ccat") :properties) "constraintValue")
                                                  :className className)
                                 )
                               superClassCCVec
                               )
                          )]
    CCQueriesVec	
    )
  )

(defn addRelApplicableTypeQuery
  "Returns the query of the function addRelApplicableType without doing a check 
  on the existence and uniqueness of applicableClass.
  :className should be className of relation class.
  :applicationType should be either SOURCE or TARGET as string.
  :applicableClassName should be a className of the source or target Node Class"
  [& {:keys [:className
             :applicationType
             :applicableClassName]}
   ]
  {:pre [(string? className)
         (contains? #{"Source" "Target"} applicationType)
         (string? applicableClassName)
         (= 1 (count (getNodes :label "Class"
                               :parameters {"className" className
                                            "classType" "RELATION"}
                               )
                     )
            )
         ]
   }
  (let [builtQuery (createRelation :fromNodeLabel "Class"
                                   :fromNodeParameters {"className" className
                                                        "classType" "RELATION"}
                                   :relationshipType (str "Applicable"applicationType"NT")
                                   :relationshipParameters {}
                                   :toNodeLabel "Class"
                                   :toNodeParameters {"className" applicableClassName
                                                      "classType" "NODE"}
                                   :execute? false
                                   :unique? true)
        ]
    builtQuery
    )
  )

(defn addSubClassAppTypeQueryVec
  "Returns a vector of queries consisting of the queries 
  for adding superclass ApplicableTypeRelations to the subclass"
  [& {:keys [:className :subClassOf]}]
  (let
      [[superClassName] subClassOf
       superClassAppSourceNTVec (vec (getClassApplicableSourceNT :className (str superClassName)))
       AppSourceNTQueriesVec (into []
                                   (map (fn
                                          [SingleMap]
                                          (addRelApplicableTypeQuery :className (SingleMap "className")
                                                                     :applicationType "Source"
                                                                     :applicableClassName className)
                                          )
                                        superClassAppSourceNTVec
                                        )
                                   )
       superClassAppTargetNTVec (vec (getClassApplicableTargetNT :className (str superClassName)))
       AppNTQueriesVec (into AppSourceNTQueriesVec
                             (map (fn
                                    [SingleMap]
                                    (addRelApplicableTypeQuery :className (SingleMap "className")
                                                               :applicationType "Target"
                                                               :applicableClassName className)
                                    )
                                  superClassAppTargetNTVec
                                  )
                             )
       ]
    AppNTQueriesVec	
    )
  )

(defn createClass
  "Create a node with label Class
  :subClassOf should be a vector containing the name of the superclass if any"
  [& {:keys [:className
             :classType
             :isAbstract?
             :subClassOf
             :properties
             :execute?]
      :or {:execute? true
           :subClassOf []}
      }
   ]
  {:pre [
         (string? className)
         (contains? #{"NODE" "RELATION"} classType)
         (not
          (or (contains? properties "className")
              (contains? properties "classType")
              (contains? properties "isAbstract")
              )
          )
       	 ]
   }
  (let [createNewNodeQuery 
        (createNewNode 	:label "Class"
                        :parameters (assoc properties
                                           "className" className
                                           "classType" classType
                                           "isAbstract" isAbstract?
                                           )
                        :execute? false
                        :unique? true
			)
        ]
    (if (not (empty? subClassOf))
                                        ;"Adds the attributes,NeoConstraints,CustomConstraints and ApplicableRelationNTs of the superclass to the subclass"
      (let
          [completeQueryVec (vec
                             (concat [createNewNodeQuery]
                                     (addSubClassATQueryVec :className className
                                                            :subClassOf subClassOf)
                                     (addSubClassCCQueryVec :className className
                                                            :subClassOf subClassOf)
                                     (addSubClassAppTypeQueryVec :className className
                                                                 :subClassOf subClassOf)
                                     )		
                             )
           NCQueryVec (addSubClassNCQueryVec :className className
                                             :subClassOf subClassOf
                                             :classType classType)
           [superClassName] subClassOf]
        (if (not (empty? (getNodes :label "Class"
                                   :parameters {"className" (str superClassName)}
                                   :execute? true)
                         )
                 )
          (if
              execute?
            (apply gdriver/runTransactions completeQueryVec NCQueryVec)
            (into completeQueryVec NCQueryVec)	
            )
          (gdriver/runQuery)
          )
        )
      (if
          execute?
        (gdriver/runQuery createNewNodeQuery)
        createNewNodeQuery
        )
      )
    )
  )

(defn getClassType
  "Gets the classType of a Class"
  [&{:keys [:className]}]
  {:pre [(string? className)]}
  (try ((into {} ((first (getNodes :label "Class"
                                   :parameters {"className" className}
                                   )
                         ) :properties)
              ) "classType")
       (catch Exception E
         (throw (Exception.
                 (str "Failed to fetch classType"
                      (.getMessage E)
                      )
                 )
                )
         )
       )
  )

(defn getClassInstances
  "Get Class Instances"
  [& {:keys [:className
             :parameters
             :count?]
      :or {:count false
           :parameters {}}}]
  {:pre [(string? className)]}
  (if (= "NODE" (getClassType :className className))
    (getNodes :count? count? :label className :parameters parameters)
    (getRelations :count? count? :relationshipType className :relationshipParameters parameters)
    )
  )

(defn editClass
  "Edit isAbstract,className, miscelaneous properties of a class.
  :className should be string , name of class to edit.
  :newProperties should be a map with optional keys:
  -'className'
  -'isAbstract'
  :miscProperties should be a map with optional keys other than:
  -'className'
  -'classType'
  -'isAbstract'
  Changing isAbstract to true will delete all instances of the class, including relations, if the class is a node class."
  [& {:keys [:className
             :newProperties
             :miscProperties
             :forceMigrate?
             :execute?]
      :or {:newProperties {}
           :miscProperties {}
           :forceMigrate? false
           :execute? true}}]
  {:pre [(string? className)
         (clojure.set/subset? (into #{} (keys newProperties)) #{"className" "isAbstract"})
         (empty? (clojure.set/intersection (into #{} (keys miscProperties)) #{"className" "isAbstract" "classType" "UUID"}))]}
  (let [classType (getClassType :className className)
        editClassQuery (editNodeProperties :label "Class"
                                           :parameters {"className" className}
                                           :changeMap (merge newProperties
                                                             miscProperties)
                                           :execute? false)]
    (if (empty? newProperties)
      (if execute?
        (gdriver/runQuery editClassQuery)
        editClassQuery)
      (if (or (= 0 (first (getClassInstances :className className
                                             :count? true)
                          )
                 ) forceMigrate?)
        (let [dataEditQueries (reduceQueryColl (concat [editClassQuery]
                                                       [(if (and (contains? newProperties "isAbstract")
                                                                 (= true (newProperties "isAbstract")))
                                                          (case classType
                                                            "NODE" (deleteDetachNodes :label className
                                                                                      :parameters {}
                                                                                      :execute? false)
                                                            "RELATION" (deleteRelations :relationshipType className
                                                                                        :execute? false)
                                                            )
                                                          []
                                                          )
                                                        ]
                                                       [(if (contains? newProperties "className")
                                                          (renameLabels :label className
                                                                        :properties {}
                                                                        :objectType classType
                                                                        :replaceLabelMap {className (newProperties "className")}
                                                                        :execute? false)
                                                          []
                                                          )]
                                                       ))
              constraintDropQueries (reduceQueryColl (if (contains? newProperties "className")
                                                       (exemptClassNeoConstraints :className className
                                                                                  :execute? false)
                                                       []))
              constraintCreateQueries (reduceQueryColl (if (contains? newProperties "className")
                                                         (map (fn [nc] (let [neoConstraint (merge ((into {} (nc "ncat")) :properties)
                                                                                                  ((into {} (nc "neo")) :properties))]
                                                                         (applyClassNeoConstraint :className (newProperties "className")
                                                                                                  :constraintValue (neoConstraint "constraintValue")
                                                                                                  :constraintType (neoConstraint "constraintType")
                                                                                                  :constraintTarget (neoConstraint "constraintTarget")
                                                                                                  :execute? false)
                                                                         )
                                                                )
                                                              (first ((getClassNeoConstraints :className className) :results)
                                                                     )
                                                              )
                                                         []
                                                         ))
              ]
          (if execute?
            (gdriver/runTransactions constraintDropQueries
                                     dataEditQueries
                                     constraintCreateQueries)
            {:constraintDropQueries constraintDropQueries
             :dataEditQueries dataEditQueries
             :constraintCreateQueries constraintCreateQueries}
            )
          )
        (throw (Exception. "Use `:forceMigrate?` true explicitly to make functional changes to the class and it's instances automatically."))
        ))
    )
  )

(defn getClasses
  "Retrieve all classes"
  [& {:keys [:count?]
      :or {:count? false}}]
  (getNodes :label "Class"
            :parameters {}
            :count? count?))

(defn deleteClass
  "Deletes a class.
  :forceMigrate? should be true if instances, constraints, etc  are to be deleted as well"
  [& {:keys [:className
             :forceMigrate?
             :execute?]
      :or {:forceMigrate? false
           :execute? true}}]
  {:pre [(string? className)]}
  (let [classInstancesCount (first (getClassInstances :className className
                                                      :parameters {}
                                                      :count? true))
        classDelQuery (deleteDetachNodes :label "Class"
                                         :parameters {"className" className}
                                         :execute? false)
        classInstDelQuery (deleteDetachNodes :label className
                                             :execute? false)
        constraintDropQueries (exemptClassNeoConstraints :className className
                                                         :execute? false)
        constraintCreateQueries []
        dataEditQueries (reduceQueryColl [classInstDelQuery classDelQuery])]
    (if (or (= 0 classInstancesCount) forceMigrate?)
      (if execute?
        (gdriver/runTransactions constraintDropQueries
                                 dataEditQueries)
        {:constraintCreateQueries constraintCreateQueries
         :constraintDropQueries constraintDropQueries
         :dataEditQueries dataEditQueries})
      (throw (Exception. "Use `:forceMigrate?` true explicitly to make functional changes to the class and it's instances automatically.")))
    )
  )

(defn defineInitialConstraints
  "Creates Initial constraints"
  [& {:keys [:execute?]
      :or {:execute? true}}
   ]
  (let [builtQueries
        [(createNCConstraints :execute? false)
         (createATConstraints :execute? false)
         (createCATConstraints :execute? false)
         (createClassConstraints :execute? false)
         (createCFConstraints :execute? false)
         (createCCATConstraints :execute? false)
         (createVRATConstraints :execute? false)
         (createAllNeoConstraints :execute? false)]]
    (if
        execute?
      (apply gdriver/runTransactions builtQueries)
      builtQueries
      )
    )
  )

(defn validatePropertyMaps
  "Validates propertyMaps for a class with className.
  Assumes class with given className exists.
  Returns list of errors"
  [& {:keys [:className
             :propertyMapList]
      :or {:propertyMapList []}
      }
   ]
  {:pre [(string? className)
         (coll? propertyMapList)
         (every? map? propertyMapList)]}
  (let [classAttributeTypes (getClassAttributeTypes :className className)
        classATValueRestrictions (reduce (fn
                                           [fullMap classAttributeType]
                                           (let [atname ((into {} classAttributeType) "_name")
                                                 atValueRestrictions (first ((getATValueRestrictions :atName atname) :results))]
                                             (if
                                                 (= 0 (count atValueRestrictions))
                                               (assoc fullMap atname nil)
                                               (assoc fullMap atname (map #(merge (into {} ((% "cf") :properties))
                                                                                  (into {} ((% "vr") :properties)))
                                                                          atValueRestrictions)
                                                      )
                                               )
                                             )
                                           ) {} classAttributeTypes)
        classCustomConstraints (map #(let [customConstraint (merge
                                                             (into {} ((% "cf") :properties))
                                                             (into {} ((% "ccat") :properties))
                                                             )
                                           ]
                                       (assoc customConstraint "atList" (into [] (customConstraint "atList")))
                                       ) (first ((getClassCustomConstraints :className className) :results)))]
    (reduce
     (fn [errors propertyMap]
       (reduce
        (fn [x y]
          (let [property (y 0) datatype (.getName (type (y 1)))]
            (concat (if (not= 1 (count (filter #(= (select-keys % ["_name" "_datatype"])
                                                   { "_name" property
                                                    "_datatype" datatype}
                                                   )
                                               classAttributeTypes)
                                       )
                              )
                      (conj x
                            (str "Unique AttributeType : (" property "," datatype ") not found for " className " in propertyMap -->" propertyMap)
                            )
                      x
                      )
                    (reduce (fn [vrerrors atvr]
                              (if (nil? atvr)
                                vrerrors
                                (let [atvrEr (gcust/checkCustomFunction :fnName (atvr "fnName")
                                                                        :fnString (atvr "fnString")
                                                                        :fnIntegrity (atvr "fnIntegrity")
                                                                        :argumentListX [(y 1)]
                                                                        :constraintValue (atvr "constraintValue"))]
                                  (if
                                      (= true atvrEr)
                                    vrerrors
                                    (conj vrerrors atvrEr)
                                    )
                                  )
                                )
                              ) [] (classATValueRestrictions property))
                    )
            )
          )
        (concat
         errors
         (if
             (>
              (count (keys propertyMap))
              (count classAttributeTypes)
              )
           [(str "No of properties ("
                 (count (keys propertyMap))
                 ") > No of associated AttributeTypes ("
                 (count classAttributeTypes)
                 ") in " className " : " propertyMap)]
           [])
         (reduce (fn [ccerrors ccc]
                   (let [cccEr (gcust/checkCustomFunction :fnName (ccc "fnName")
                                                          :fnString (ccc "fnString")
                                                          :fnIntegrity (ccc "fnIntegrity")
                                                          :argumentListX (seq (map #(propertyMap %) (ccc "atList")))
                                                          :constraintValue (ccc "constraintValue")
                                                          )
                         ]
                     (if
                         (= true cccEr)
                       ccerrors
                       (conj ccerrors cccEr)))) [] classCustomConstraints)
         )
        (seq propertyMap)))
     []
     propertyMapList))
  )

(defn validateClassInstances
  "Validate instances of a class"
  [& {:keys [:className
             :classType
             :instList]}]
  {:pre [(string? className)
         (string? classType)
         (contains? #{"NODE"
                      "RELATION"}
                    classType)
         (coll? instList)
         (every? map? instList)]}
  (let [fetchedClass (getNodes :label "Class"
                               :parameters {"className" className
                                            "classType" classType}
                               )]
    (if
        (not= 1 (count fetchedClass))
      (throw (Exception. (str "Unique " classType " Class " className " doesn't exist")))
      )
    (if;;MARK remove into {} later
        ((into {} ((first fetchedClass) :properties)) "isAbstract")
      (throw (Exception. (str className " is Abstract")))
      )
    )
  (let [propertyErrors (validatePropertyMaps :className className
                                             :propertyMapList instList
                                             )
        ]
    (if (not= 0 (count propertyErrors))
      (throw (Exception. (str (seq propertyErrors))))
      )
    )
  )

(defn createNodeClassInstances
  "Creates nodes , as an instance of a Class with classType:NODE.
  :nodeList should be a collection of maps with node properties"
  [& {:keys [:className
             :nodeList
             :execute?]
      :or {:execute? true
           :nodeList []}
      }
   ]
  {:pre [
         (string? className)
         (coll? nodeList)
         (every? map? nodeList)
         ]
   }
  (try
    (validateClassInstances :className className
                            :classType "NODE"
                            :instList nodeList)
    (let [builtQueries (map #(createNewNode :label className
                                            :parameters % 
                                            :execute? false
                                            )
                            nodeList
                            )
          ]
      (if
          execute?
        (apply gdriver/runQuery builtQueries)
        builtQueries))
    (catch Exception E
      (.getMessage E)
      )
    )
  )

(defn createRelationClassInstances
  "Creates a relation between two nodes, as an instance of a class with classType:RELATION.
  :className : relation className
  :relList : list of maps with the following keys
  -:fromClassName className of 'out' label.
  -:fromPropertyMap a property map that matches one or more 'out' nodes.
  -:propertyMap relation propertyMap.
  -:toClassName className of 'in' label.
  -:toPropertyMap a property map that matches one or more 'in' nodes."
  [& {:keys [:className
             :relList
             :execute?]
      :or {:execute? true
           :relList []}
      }
   ]
  {:pre [
         (string? className)
         (every? string? (map #(% :fromClassName) relList))
         (every? string? (map #(% :toClassName) relList))
         (coll? relList)
         (every? map? relList)]}
  (try;;MARK remove into {}
    (let [relApplicableNTs (getRelApplicableNTs :className className)
          relSourceNTs (into #{}
                             (map #((into {} ((% "nt") :properties)) "className")
                                  (first relApplicableNTs)
                                  )
                             )
          relTargetNTs (into #{} (map #((into {} ((% "nt") :properties)) "className")
                                      (last relApplicableNTs)
                                      )
                             )
          ]
      (doall (map (fn [relation]
                    (if
                        (not (contains? relSourceNTs (relation :fromClassName)))
                      (throw (Exception. (str (relation :fromClassName)
                                              " is not an ApplicableSourceNT for "className)
                                         )
                             )
                      )
                    (if
                        (not (contains? relTargetNTs (relation :toClassName)))
                      (throw (Exception. (str (relation :toClassName)
                                              " is not an ApplicableTargetNT for "className)
                                         )
                             )
                      )
                    )
                  relList)
             )
      )
    (validateClassInstances :className className
                            :classType "RELATION"
                            :instList (map #(% :propertyMap) relList)
                            )
    (let [builtQueries (map #(createRelation
                              :fromNodeLabel (% :fromClassName)
                              :fromNodeParameters (% :fromPropertyMap)
                              :relationshipType className
                              :relationshipParameters (% :propertyMap)
                              :toNodeLabel (% :toClassName)
                              :toNodeParameters (% :toPropertyMap)
                              :execute? false
                              :unique? true) relList)]
      (if
          execute?
        (apply gdriver/runQuery builtQueries)
        builtQueries))
    (catch Exception E (.getMessage E))
    )
  )
