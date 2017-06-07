(ns gnowdb.neo4j.gneo
  (:gen-class)
  (:require [clojure.set :as clojure.set]
            [clojure.java.io :as io]
            [clojure.string :as clojure.string]
            [gnowdb.neo4j.gdriver :as gdriver]
            [gnowdb.neo4j.gcust :as gcust]))

(defn- addStringToMapKeys
  [stringMap string]
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

(defn- createEditString
  "Creates an edit string.
  eg.., varName.prop1={prop1} , varName.prop2={prop2}
  :varName should be name of the node/relation variable.
  :editPropertyList should be a collection of properties."
  [& {:keys [:varName :editPropertyList :characteristicString] :or {:characteristicString ""}}]
  {:pre [(string? varName)
         (coll? editPropertyList)
         (every? string? editPropertyList)]}
  (str " SET "
       (clojure.string/join " ,"
                            (map #(str varName"."%1" = {"%2"}")
                                 (removeVectorStringSuffixes editPropertyList characteristicString)
                                 editPropertyList)
                            )
       )
  )

(defn- createRemString
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

;;General NEO4J functions start here

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
  [& {:keys [label parameters execute? unique?] :or {execute? true unique? false parameters {}}}]
  (let [queryType 
 	(if unique?
  		"MERGE"
  		"CREATE"
  	)
  	builtQuery
  	{:query (str queryType " (node:" label " " (createParameterPropertyString parameters) " )") :parameters parameters}
  	]
  	(if execute?
    	((gdriver/runQuery builtQuery) :summary)
    	builtQuery
   	)
  )
)  

(defn createRelation
  "Relate two nodes matched with their properties (input as clojure map) with it's own properties"
  [& {:keys [fromNodeLabel fromNodeParameters relationshipType relationshipParameters toNodeLabel toNodeParameters execute? unique?] :or {execute? true unique? false toNodeParameters {} fromNodeParameters {} relationshipParameters {}}}]
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
         (str "MATCH (node1:" fromNodeLabel " " ((combinedProperties :propertyStringMap) "1") " ) , (node2:" toNodeLabel " " ((combinedProperties :propertyStringMap) "2") " ) CREATE " unique " (node1)-[:" relationshipType " " ((combinedProperties :propertyStringMap) "R") " ]->(node2)") :parameters (combinedProperties :combinedPropertyMap)}]
    (if execute?
      (gdriver/runQuery builtQuery)
      builtQuery
      )
    )
  )

(defn deleteDetachNodes
  "Delete node(s) matched using property map and detach (remove relationships)"
  [& {:keys [label parameters execute?] :or {execute? true parameters {}}}]
  (if execute?
    ((gdriver/runQuery {:query (str "MATCH (node:" label " " (createParameterPropertyString parameters) " ) DETACH DELETE node") :parameters parameters}) :summary)
    {:query (str "MATCH (node:" label " " (createParameterPropertyString parameters) " ) DETACH DELETE node") :parameters parameters}
    )
  )

(defn deleteNodes
  "Delete node(s) matched using property map"
  [& {:keys [label parameters execute?] :or {execute? true parameters {}}}]
  (let [builtQuery {:query (str "MATCH (node:"label" "(createParameterPropertyString parameters)" ) DELETE node") :parameters parameters}]
    (if
        execute?
      (gdriver/runQuery builtQuery)
      builtQuery
      )
    )
  )

(defn editNodeProperties
  "Edit Properties of Node(s)"
  [& {:keys [label parameters changeMap execute?] :or {execute? true parameters {}}}]
  (let [mPM (addStringToMapKeys parameters "M") tPME (addStringToMapKeys changeMap "E") builtQuery {:query (str "MATCH (node1:" label " " (createParameterPropertyString mPM "M") " ) " (createEditString :varName "node1" :editPropertyList tPME :characteristicString "E")) :parameters (merge mPM tPME)}]
    (if
        execute?
      (gdriver/runQuery builtQuery)
      builtQuery)
    )
  )

(defn removeNodeProperties
  "Remove properties from Node"
  [& {:keys [label parameters removeProperties execute?] :or {execute? true parameters {}}}]
  (let [builtQuery {:query (str "MATCH (node1:"label" "(createParameterPropertyString parameters)" ) "(createRemString :varName "node1" :remPropertyList removeProperties)) :parameters parameters}]
    (if
        execute?
      (gdriver/runQuery builtQuery)
      builtQuery)
    )
  )

(defn getNodes
  "Get Node(s) matched by label and propertyMap"
  [& {:keys [:label :parameters :execute?] :or {:parameters {} :execute? true}}]
  {:pre [(string? label)]}
  (if execute?
    (map #(% "node") (((gdriver/runQuery {:query (str "MATCH (node:" label " " (createParameterPropertyString parameters) ") RETURN node") :parameters parameters}) :results) 0))
    {:query (str "MATCH (node:" label " " (createParameterPropertyString parameters) ") RETURN node") :parameters parameters}
    )
  )

(defn getRelations
  "Get relations matched by inNode/outNode/type and properties"
  [& {:keys [fromNodeLabel fromNodeParameters relationshipType relationshipParameters toNodeLabel toNodeParameters execute?] :or {execute? true toNodeParameters {} fromNodeParameters {} relationshipParameters {} fromNodeLabel "" toNodeLabel "" relationshipType ""}}]
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
         (str "MATCH (n" fromNodeLabel " " ((combinedProperties :propertyStringMap) "1") ")-[p" relationshipType " " ((combinedProperties :propertyStringMap) "R") "]->(m" toNodeLabel " " ((combinedProperties :propertyStringMap) "2") ") RETURN p")
         :parameters
         (combinedProperties :combinedPropertyMap)
         }
        ]
    (if execute?
      (map #(% "p") (first ((gdriver/runQuery builtQuery) :results)))
      builtQuery
      )
    )
  )

(defn getNeighborhood
  "Get the neighborhood of a particular node"
  [& {:keys [:label :parameters] :or {:parameters {}}}]
  (let [nodeseq (getNodes :label label :parameters parameters)]
    (if (not= (count nodeseq) 1)
      "Error"
      (let [nodeLabel ((first nodeseq) :labels)
            nodeParameters ((first nodeseq) :properties)
            ]
        {
         :labels nodeLabel
         :properties nodeParameters
         :outNodes (map #(select-keys % [:labels :properties :toNode]) (getRelations :fromNodeLabel nodeLabel :fromNodeParameters nodeParameters))
         :inNodes (map #(select-keys % [:labels :properties :fromNode]) (getRelations :toNodeLabel nodeLabel :toNodeParameters nodeParameters))
         }
        )
      )
  )
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
      (reduceQueryColl %1)
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
  [& {:keys [:label :CD :propertyVec :constraintType :execute?] :or {:execute? true}}]
  {:pre [
         (string? label)
         (contains? #{"CREATE" "DROP"} CD)
         (not (empty? propertyVec))
         (contains?   #{"UNIQUE" "NODEEXISTANCE" "RELATIONEXISTANCE" "NODEKEY"} constraintType)
         (if
             (= "NODEKEY" constraintType)
           (every? #(and (coll? %) (not (empty? %))) propertyVec)
           true
           )
         ]
   }
  (let [queryBuilder (case constraintType
                       "UNIQUE" #(str "(label:" label ") ASSERT label." % " IS UNIQUE")
                       "NODEEXISTANCE" #(str "(label:" label ") ASSERT exists(label." % ")")
                       "RELATIONEXISTANCE" #(str "()-[label:" label "]-() ASSERT exists(label." % ")")
                       "NODEKEY" #(str
                                   "(label:" label ") ASSERT (" (clojure.string/join
                                                                 ", "
                                                                 (map (fn [property] (str "label." property)) %)
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
  [& {:keys [:label :CD :propertyVec :execute?] :or {:execute? true} :as keyArgs}]
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
  [& {:keys [:label :CD :propertyVec :NR :execute?] :or {:execute? true} :as keyArgs}]
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
  [& {:keys [:label :CD :propPropVec :execute?] :or {:execute? true} :as keyArgs}]
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
  (manageNodeKeyConstraints :label "NeoConstraint" :CD "CREATE" :propPropVec [["constraintType" "constraintTarget"]] :execute? execute?))

(defn createCATConstraints
  "Create Constraints that apply to relationships with label NeoConstraintAppliesTo"
  [& {:keys [:execute?] :or {:execute? true}}]
  (manageExistanceConstraints :label "NeoConstraintAppliesTo" :CD "CREATE" :propertyVec ["constraintValue"] :NR "RELATION" :execute? execute?))

(defn createATConstraints
  "Creates Constraints that apply to nodes with label AttributeType"
  [& {:keys [:execute?] :or {:execute? true}}]
  (manageNodeKeyConstraints :label "AttributeType" :CD "CREATE" :propPropVec [["_name"]] :execute? execute?))

(defn createCFConstraints
  "Creates Constraints that apply to nodes with label CustomFunction"
  [& {:keys [:execute?] :or {:execute? true}}]
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
  [& {:keys [:execute?] :or {:execute? true}}]
  (manageExistanceConstraints :label "ValueRestrictionAppliesTo" :CD "CREATE" :propertyVec ["constraintValue"] :NR "RELATION" :execute? execute?))

(defn createCCATConstraints
  "Creates Constraints that apply to relations with label CustomConstraintAppliesTo"
  [& {:keys [:execute?] :or {:execute? true}}]
  (manageExistanceConstraints :label "CustomConstraintAppliesTo" :CD "CREATE" :propertyVec ["constraintValue" "atList"] :NR "RELATION" :execute? execute?))

(defn createClassConstraints
  "Create Constraints that apply to nodes with label Class"
  [& {:keys [:execute?] :or {:execute? true}}]
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
  [& {:keys [:constraintType :constraintTarget :execute?] :or {:execute? true} :as keyArgs}]
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
                 :parameters {"constraintType" constraintType "constraintTarget" constraintTarget}
                 :execute? execute?
                 )
  )

(defn createAllNeoConstraints
  "Creates all NeoConstraints"
  [& {:keys [:execute?] :or {:execute? true}}]
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

(defn createAttributeType
  "Creates a node with Label AttributeType.
  :_name should be a string
  :_datatype should be a string of one of the following: 'java.lang.Boolean', 'java.lang.Byte', 'java.lang.Short', 'java.lang.Integer', 'java.lang.Long', 'java.lang.Float', 'java.lang.Double', 'java.lang.Character', 'java.lang.String', 'java.util.ArrayList'"
  [& {:keys [:_name :_datatype :execute?] :or {:execute? true} :as keyArgs}]
  {:pre [
         (string? _name)
         (contains? validATDatatypes _datatype)
         ]
   }
  (createNewNode :label "AttributeType"
                 :parameters {"_name" _name "_datatype" _datatype}
                 :execute? execute?)
  )


(defn createCustomFunction
  "Creates a customFunction.
  :fnName should be string.
  :fnString should be string that represents CustomFunction template."
  [& {:keys [:fnName :fnString :execute?] :or {:execute? true}}]
  {:pre [(gcust/stringIsCustFunction? fnString)
         (string? fnName)]}
  (createNewNode :label "CustomFunction"
                 :parameters {"fnName" fnName
                              "fnString" fnString
                              "fnIntegrity" (gcust/hashCustomFunction fnString)}
                 :execute? execute?))

(defn getClassAttributeTypes
  "Get all AttributeTypes 'attributed' to a class"
  [className]
  (map #((% "att") :properties) (((gdriver/runQuery
     {:query "MATCH (class:Class {className:{className}})-[rel:HasAttributeType]->(att:AttributeType) RETURN att"
      :parameters {"className" className}
      }
     ) :results) 0))
  )

(defn getClassNeoConstraints
  "Get all NeoConstraints attributed to a class"
  [className]
  (gdriver/runQuery
   {:query "MATCH (class:Class {className:{className}})<-[ncat:NeoConstraintAppliesTo]-(neo:NeoConstraint) RETURN ncat,neo"
    :parameters {"className" className}
    }
   )
  )

(defn getClassCustomConstraints
  "Get all CustomConstraints applicable to a class"
  [className]
  (gdriver/runQuery
   {:query "MATCH (class:Class {className:{className}})<-[ccat:CustomConstraintAppliesTo]-(cf:CustomFunction) RETURN ccat,cf"
    :parameters {"className" className}
    }
   )
  )

(defn getATValueRestrictions
  "Get all ValueRestriction applicable to an AttributeType with _name atname"
  [atname]
  (gdriver/runQuery
   {:query "MATCH (at:AttributeType {_name:{atname}})<-[vr:ValueRestrictionAppliesTo]-(cf:CustomFunction) RETURN cf,vr"
    :parameters {"atname" atname}
    }
   )
  )

(defn applyClassNeoConstraint
  "Apply a NeoConstraint that apply to a class.
  :className string
  :constraintType UNIQUE,NODEKEY,EXISTANCE
  :constraintTarget NODE,RELATION
  :constraintValue depends upon :_constraintTarget and :_constraintType
  :execute?"
  [& {:keys [:className :constraintType :constraintTarget :constraintValue :execute?] :or {:execute? true} :as keyArgs}]
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

(defn applyClassNeoConstraints
  "Apply all NeoConstraints for a class"
  [& {:keys [:className :execute?] :or {:execute? true} :as keyArgs}]
  (let [builtQueries (reduceQueryColl
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
                       (((getClassNeoConstraints className) :results) 0)
                       )
                      )
        ]
    (if
        execute?
      (apply gdriver/runQuery builtQueries)
      builtQueries)
    )
  )

(defn addRelApplicableType
  "Add an applicable Source/Target type to a Relation Class, by creating a relation: ApplicableSourceNT/ApplicableTargetNT.
  :className should be className of relation class.
  :applicationType should be either SOURCE or TARGET as string.
  :applicableClassName should be a className of the source or target Node Class"
  [& {:keys [:className :applicationType :applicableClassName :execute?] :or {:execute? true}}]
  {:pre [(string? className)
         (contains? #{"Source" "Target"} applicationType)
         (string? applicableClassName)
         (= 1 (count (getNodes :label "Class" :parameters {"className" className "classType" "RELATION"})))
         (= 1 (count (getNodes :label "Class" :parameters {"className" applicableClassName "classType" "NODE"})))
         ]
   }
  (let [builtQuery (createRelation :fromNodeLabel "Class" :fromNodeParameters {"className" className "classType" "RELATION"} :relationshipType (str "Applicable"applicationType"NT") :relationshipParameters {} :toNodeLabel "Class" :toNodeParameters {"className" applicableClassName "classType" "NODE"} :execute? false :unique? true)]
    (if
        execute?
      (gdriver/runQuery builtQuery)
      builtQuery)
    )
  )

(defn getRelApplicableNTs
  "Get a relation class' ApplicableNTs.
  :className should be of a Class of classType 'RELATION'."
  [& {:keys [:className]}]
  {:pre [(string? className)]}
  (let [combinedPropertyMap (combinePropertyMap {"RT" {"className" className "classType" "RELATION"} "NT" {"classType" "NODE"}})
        builtQuery1 {:query (str "MATCH (rt:Class "((combinedPropertyMap :propertyStringMap) "RT")")-[:ApplicableSourceNT]->(nt:Class "((combinedPropertyMap :propertyStringMap) "NT")") RETURN nt") :parameters (combinedPropertyMap :combinedPropertyMap)}
        builtQuery2 {:query (str "MATCH (rt:Class "((combinedPropertyMap :propertyStringMap) "RT")")-[:ApplicableTargetNT]->(nt:Class "((combinedPropertyMap :propertyStringMap) "NT")") RETURN nt") :parameters (combinedPropertyMap :combinedPropertyMap)}
        ]
    ((gdriver/runQuery builtQuery1 builtQuery2) :results)
    )
  )

(defn addClassAT
  "Adds a relation HasAttributeType from Class to AttributeType.
  :_atname: _name of AttributeType.
  :_atdatatype: _datatype of AttributeType.
  :className: className of Class"
  [& {:keys [:_atname :className :execute?] :or {:execute? true}}]
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
                  :execute? execute?)
  )

(defn addSubClassAT
	[& {:keys [:className :subClassOf :createNewNodeQuery :execute?] :or {:execute? true}}] 
	(let [[superClassName] subClassOf superClassATVec (vec (getClassAttributeTypes (str superClassName))) 
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
					(into [createNewNodeQuery is_aRelationQuery]
						(
							map (fn
									[SingleATMap] 
									(addClassAT :_atname (SingleATMap "_name") :className className :execute? false)
								)
							superClassATVec
						)
					)]
		(if execute? 
			((apply gdriver/runQuery queriesVec) :summary)
			queriesVec
		)					
	)
)

(defn createClass
  "Create a node with label Class"
  [& {:keys [:className :classType :isAbstract?	:subClassOf :properties :execute?] :or {:execute? true :subClassOf []}}]
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
			)
		]
		(if (not (empty? subClassOf))
			;;Adds the attributes of the superclass to the subclass
			(addSubClassAT :className className :subClassOf subClassOf :createNewNodeQuery createNewNodeQuery :execute? execute?) 
  			(
  				if execute?
				((gdriver/runQuery createNewNodeQuery) :summary)
				createNewNodeQuery
			)
 		)
	)
)   

(defn addATVR
  "Adds a ValueRestriction to an AttributeType.
  Creates a relation ValueRestrictionAppliesTo from CustomFunction to AttributeType.
  :_atname should be _name of an AttributeType.
  :fnName should be fnName of a CustomFunction.
  :constraintValue should be value to be passed as CustomFunction's second argument"
  [& {:keys [:_atname :fnName :constraintValue :execute?] :or {:execute? true}}]
  {:pre [(string? _atname)
         (string? fnName)]}
  (createRelation :fromNodeLabel "CustomFunction"
                  :fromNodeParameters {"fnName" fnName}
                  :relationshipType "ValueRestrictionAppliesTo"
                  :relationshipParameters {"constraintValue" constraintValue}
                  :toNodeLabel "AttributeType"
                  :toNodeParameters {"_name" _atname}
                  :execute? execute?)
  )

(defn addClassNC
  "Adds a relation NeoConstraintAppliesTo from NeoConstraint to Class.
  :constraintType should be either of UNIQUE,EXISTANCE,NODEKEY.
  :constraintTarget should be either of NODE,RELATION.
  :constraintValue should be _name of an  AttributeType or collection of _names, in case of NODEKEY"
  [& {:keys [:constraintType :constraintTarget :constraintValue :className :execute?] :or {:execute? true}}]
  {:pre [
         (string? className)
         (= 1 (count (getNodes :label "Class"
                               :parameters {"className" className "classType" constraintTarget}
                               )
                     )
            )
         ]
   }
  (createRelation :fromNodeLabel "NeoConstraint"
                  :fromNodeParameters {"constraintType" constraintType
                                       "constraintTarget" constraintTarget}
                  :relationshipType "NeoConstraintAppliesTo"
                  :relationshipParameters {"constraintValue" constraintValue}
                  :toNodeLabel "Class"
                  :toNodeParameters {"className" className}
                  :execute? execute?)
  )

(defn addClassCC
  "Adds a relation CustomConstraintAppliesTo from CustomFunction to Class.
  :fnName of a CustomFunction.
  :atList should be list of AttributeTypes' _name.
  :constraintValue should be value to be passed as CustomFunction's second argument"
  [& {:keys [:fnName :atList :constraintValue :className :execute?] :or {:execute? true}}]
  {:pre [(string? className)
         (string? fnName)
         (coll? atList)
         (every? string? atList)]}
  (let [classAttributeTypes (getClassAttributeTypes className)]
    (if (not (every? #(= 1 (count (filter (fn [at] (= % ((into {} at) "_name"))) classAttributeTypes))) atList))
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

(defn gnowdbInit
  "Create Initial constraints"
  [& {:keys [:execute?]  :or {:execute? true}}]
  (let [builtQueries
        (reduceQueryColl [(createNCConstraints :execute? false)
                          (createATConstraints :execute? false)
                          (createCATConstraints :execute? false)
                          (createClassConstraints :execute? false)
                          (createCFConstraints :execute? false)
                          (createCCATConstraints :execute? false)
                          (createVRATConstraints :execute? false)
                          (createAllNeoConstraints :execute? false)])]
    (if
        execute?
      (apply gdriver/runQuery builtQueries)
      builtQueries)
    )
  )

(defn validatePropertyMaps
  "Validates propertyMaps for a class with className.
  Assumes class with given className exists.
  Returns list of errors"
  [& {:keys [:className :propertyMapList] :or {:propertyMapList []}}]
  {:pre [
         (string? className)
         (coll? propertyMapList)
         (every? map? propertyMapList)]}
  (let [classAttributeTypes (getClassAttributeTypes className)
        classATValueRestrictions (reduce (fn
                                           [fullMap classAttributeType]
                                           (let [atname ((into {} classAttributeType) "_name") atValueRestrictions (first ((getATValueRestrictions atname) :results))]
                                             (if
                                                 (= 0 (count atValueRestrictions))
                                               (assoc fullMap atname nil)
                                               (assoc fullMap atname (map #(merge (into {} ((% "cf") :properties)) (into {} ((% "vr") :properties))) atValueRestrictions)
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
                                      ) (first ((getClassCustomConstraints className) :results)))]
    (reduce
     (fn [errors propertyMap]
       (reduce
        (fn [x y]
          (let [property (y 0) datatype (.getName (type (y 1)))]
            (concat (if (not= 1 (count (filter #(= % {"_name" property
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
                    (reduce (fn [vrerrors atvr] (if (nil? atvr)
                                                  vrerrors
                                                  (let [atvrEr (gcust/checkCustomFunction :fnName (atvr "fnName") :fnString (atvr "fnString") :fnIntegrity (atvr "fnIntegrity") :argumentListX [(y 1)] :constraintValue (atvr "constraintValue"))]
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
           [(str "No of properties (" (count (keys propertyMap)) ") > No of associated AttributeTypes (" (count classAttributeTypes) ") in " className " : " propertyMap)]
           [])
         (reduce (fn [ccerrors ccc]
                   (let [cccEr (gcust/checkCustomFunction :fnName (ccc "fnName") :fnString (ccc "fnString") :fnIntegrity (ccc "fnIntegrity") :argumentListX (seq (map #(propertyMap %) (ccc "atList"))) :constraintValue (ccc "constraintValue"))]
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
  [& {:keys [:className :classType :instList]}]
  {:pre [(string? className)
         (string? classType)
         (contains? #{"NODE" "RELATION"} classType)
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
  [& {:keys [:className :nodeList :execute?] :or {:execute? true :nodeList []}}]
  {:pre [
         (string? className)
         (coll? nodeList)
         (every? map? nodeList)
         ]
   }
  (try
    (validateClassInstances :className className :classType "NODE" :instList nodeList)
    (let [builtQueries (map #(createNewNode :label className
                                            :parameters %
                                            :execute? false
                                            ) nodeList
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

(defn createRelationClassInstance
  "Creates a relation between two nodes, as an instance of a class with classType:RELATION.
  :className : relation className
  :relList : list of maps with the following keys
  -:fromClassName className of 'out' label.
  -:fromPropertyMap a property map that matches one or more 'out' nodes.
  -:propertyMap relation propertyMap.
  -:toClassName className of 'in' label.
  -:toPropertyMap a property map that matches one or more 'in' nodes."
  [& {:keys [:className :relList :execute?] :or {:execute? true :relList []}}]
  {:pre [
         (string? className)
         (every? string? (map #(% :fromClassName) relList))
         (every? string? (map #(% :toClassName) relList))
         (coll? relList)
         (every? map? relList)]}
  (try;;MARK remove into {}
    (let [relApplicableNTs (getRelApplicableNTs :className className)
          relSourceNTs (into #{} (map #((into {} ((% "nt") :properties)) "className") (first relApplicableNTs)))
          relTargetNTs (into #{} (map #((into {} ((% "nt") :properties)) "className") (last relApplicableNTs)))
          ]
      (doall (map (fn [relation]
                    (if
                        (not (contains? relSourceNTs (relation :fromClassName)))
                      (throw (Exception. (str (relation :fromClassName)" is not an ApplicableSourceNT for "className))))
                    
                    (if
                        (not (contains? relTargetNTs (relation :toClassName)))
                      (throw (Exception. (str (relation :toClassName)" is not an ApplicableTargetNT for "className))))) relList)))
    (validateClassInstances :className className :classType "RELATION" :instList (map #(% :propertyMap) relList))
    (let [builtQueries (map #(createRelation
                              :fromNodeLabel (% :fromClassName)
                              :fromNodeParameters (% :fromPropertyMap)
                              :relationshipType className
                              :relationshipParameters (% :propertyMap)
                              :toNodeLabel (% :toClassName)
                              :toNodeParameters (% :toPropertyMap)
                              :execute? false) relList)]
      (if
          execute?
        (apply gdriver/runQuery builtQueries)
        builtQueries))
    (catch Exception E (.getMessage E))
    )
  )

