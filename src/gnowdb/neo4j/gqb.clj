(ns gnowdb.neo4j.gqb
  (:gen-class)
  (:require [clojure.set :as clojure.set]
            [clojure.string :as clojure.string]))
;; Query String building functions

(defn ddistinct
  [coll]
  {:pre? [(coll? coll)]}
  (if (or (set? coll)
          (map? coll))
    coll
    (distinct coll)))

(defn backtick
  [string]
  (let [strr (str string)
        splitString (clojure.string/split strr #"")]
    (if (or (and (= "`" (first splitString))
                 (= "`" (last splitString)))
            (= "" strr))
      strr
      (str "`"strr"`"))))

(defn remBacktick
  [string]
  (let [strr (str string)
        splitString (clojure.string/split strr #"")]
    (if (and (= "`" (first splitString))
             (= "`" (last splitString)))
      (subs strr 1 (- (count splitString) 1))
      strr)))

(defn isClassName?
  [string]
  (let [strr (str string)
        splitString (clojure.string/split strr #"")]
    (if (or (= "`" (first splitString))
            (= "`" (last splitString)))
      false
      true)))

(defn createReturnString
  "Creates a string for the following : ' RETURN var.prop as propalt,..'
  Each triplet should be of the form ['var' 'prop' 'propalt'] or ['var' 'prop']"
  [& triplets]
  {:pre [(every? coll? triplets)
         (every? #(every? string? %) triplets)]}
  (if (empty? triplets)
    ""
    (str " RETURN "
         (clojure.string/join ", "
                              (map #(str (% 0)"."(% 1)
                                         (if (> (count %) 2)
                                           (str " as "
                                                (% 2)))) triplets)))))

(defn createLabelString
  "Create a label string from a collection of label strings.
  :labels should be a map of strings.
   Individual labels will be enclosed with backticks if they are not already to allow for special characters, etc.
  :labels will be joined and preceded with ':'"
  [& {:keys [:labels]
      :or {:labels []}}]
  {:pre [(coll? labels)]}
  (let [labelsx (reduce #(if (or (nil? %2)
                                 (= "" %2))
                           %1
                           (conj %1 %2)) [] (ddistinct labels))]
    (if (empty? labelsx)
      ""
      (str ":"
           (clojure.string/join ":" (map #(backtick %) labelsx))
           " "))))

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

(defn removeVectorStringSuffixes
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

(defn createParameterPropertyString
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

(defn combinePropertyMap
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
      :or {:characteristicString ""
           :editPropertyList []}}]
  (if (not (coll? editPropertyList))
    (println editPropertyList))
  {:pre [(string? varName)
         (coll? editPropertyList)
         (every? string? editPropertyList)]}
  (if (not (empty? editPropertyList))
    (str " SET "
         (clojure.string/join " ,"
                              (map #(str varName "." %1 " = {" %2 "}")
                                   (removeVectorStringSuffixes editPropertyList characteristicString)
                                   editPropertyList)
                              )
         )
    "")
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
  (if (empty? remPropertyList)
    " "
    (str "REMOVE "
         (clojure.string/join ", "
                              (vec (map #(str varName"."%1) 
                                        remPropertyList
                                        )	
                                   )
                              )
         )
    ))

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
      :or {:replaceVal ""}}]
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
      :or {:replaceVal ""
           :withWhere? true}}]
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
