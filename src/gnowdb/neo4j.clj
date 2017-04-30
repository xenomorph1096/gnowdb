(ns gnowdb.neo4j)
(import '[org.neo4j.driver.v1 Driver AuthTokens GraphDatabase Record Session StatementResult Transaction Values])


(defn getNeo4jDBDetails
  "Get Neo4jDB Details :host address,username,password "
  []
  ["bolt://localhost:7687" "neo4j" "neo4j"])

(defn parseRecordFields
  "Parse Record Fields(list of key value pairs)"
  [fieldList]
  (def fieldMap {})
  (loop [x 0]
    (when (< x (.size fieldList))
      ;; (println (str (.value (.get fieldList x))))
      (def fieldMap (assoc fieldMap :key (str (.value (.get fieldList x)))))
      (def fieldMap (clojure.set/rename-keys fieldMap {:key (str (.key (.get fieldList x)))}))
      (recur (+ x 1))))
  fieldMap)


(defn parseStatementRecordList
  "Convert Record List to String clojure list"
  [recordList]
  (def retVec (vector))
  (loop [x 0]
    (when (< x (.size recordList))
      (def retVec (conj retVec (parseRecordFields (.fields (.get recordList x)))))
      (recur (+ x 1))))
  retVec)

(defn createSummaryMap
  "Creates a summary map from StatementResult object.
  This Object is returned by the run() method of session object
  To be used for cypher queries that dont return nodes
  Driver should not be closed before invoking this function"
  [statementResult]
  (def summaryCounters (.counters (.consume statementResult)))
  (def summaryMap {:constraintsAdded (.constraintsAdded summaryCounters) :constraintsRemoved (.constraintsRemoved summaryCounters) :containsUpdates (.containsUpdates summaryCounters) :indexesAdded (.indexesAdded summaryCounters) :indexesRemoved (.indexesRemoved summaryCounters) :labelsAdded (.labelsAdded summaryCounters) :labelsRemoved (.labelsRemoved summaryCounters) :nodesCreated (.nodesCreated summaryCounters) :nodesDeleted (.nodesDeleted summaryCounters) :propertiesSet (.propertiesSet summaryCounters) :relationshipsCreated (.relationshipsCreated summaryCounters) :relationshipsDeleted (.relationshipsDeleted summaryCounters)})
    summaryMap)

(defn getSummaryIfNonZero
  "Returns Summary if Value is non zero"
  [mapValue summarySubString]
  (if (= mapValue 0)
    (def sumSubString (str ""))
    (def sumSubString (str summarySubString mapValue " ;")))
  sumSubString)

(defn createSummaryString
  "Creates Summary String only with only necessary information.
  Takes summaryMap created by createSummaryMap function."
  [summaryMap]
  (def summaryString (str "Contains Updates : " (summaryMap :containsUpdates) " ; "))
  (def summaryString (str summaryString (getSummaryIfNonZero (summaryMap :constraintsAdded) " Constraints Added : ")))
  (def summaryString (str summaryString (getSummaryIfNonZero (summaryMap :constraintsRemoved) " Constraints Removed: ")))
  (def summaryString (str summaryString (getSummaryIfNonZero (summaryMap :indexesAdded) " Indexes Added: ")))
  (def summaryString (str summaryString (getSummaryIfNonZero (summaryMap :indexesRemoved) " Indexes Removed: ")))
  (def summaryString (str summaryString (getSummaryIfNonZero (summaryMap :labelsAdded) " Labels Added: ")))
  (def summaryString (str summaryString (getSummaryIfNonZero (summaryMap :labelsRemoved) " Labels Removed: ")))
  (def summaryString (str summaryString (getSummaryIfNonZero (summaryMap :nodesCreated) " Nodes Created: ")))
  (def summaryString (str summaryString (getSummaryIfNonZero (summaryMap :nodesDeleted) " Nodes Deleted: ")))
  (def summaryString (str summaryString (getSummaryIfNonZero (summaryMap :propertiesSet) " Properties Set: ")))
  (def summaryString (str summaryString (getSummaryIfNonZero (summaryMap :relationshipsCreated) " Relationships Created: ")))
  (def summaryString (str summaryString (getSummaryIfNonZero (summaryMap :relationshipsDeleted) " Relationships Deleted: ")))
  summaryString)

(defn getFullSummary
  "Returns a vector of summaryMap and summaryString"
  [statementResult]
  (def stMap (createSummaryMap statementResult))
  [stMap (createSummaryString stMap)])

(defn getAllLabels
  "Get all the Labels from the graph"
  []
  (def neo4jDBDetails (getNeo4jDBDetails))
  (def driver (GraphDatabase/driver (get neo4jDBDetails 0) (AuthTokens/basic (get neo4jDBDetails 1) (get neo4jDBDetails 2))))
  (def session (.session driver))
  (def stList (.list (.run session "match (n) return distinct labels(n)")))
  (.close driver)
  stList)


(defn getAllLabelsParsed
  "Get All Labels and parse Record List"
  []
  (parseStatementRecordList (getAllLabels)))



(defn getAllNodes
  "Get All Nodes In Graph"
  []
  (def neo4jDBDetails (getNeo4jDBDetails))
  (def driver (GraphDatabase/driver (get neo4jDBDetails 0) (AuthTokens/basic (get neo4jDBDetails 1) (get neo4jDBDetails 2))))
  (def session (.session driver))
  (def stList (.list (.run session "match (n) return n")))
  (.close driver)
  stList)

(defn getNodeKeys
  "Gets Node Keys as seq using NodeValue"
  [nodeValue]
  (iterator-seq (.iterator (.keys nodeValue))))

(defn parsePlainNode
  "Parse plain Node and their key values"
  [plainNode]
  (def nodeFields (.fields plainNode))
  (def fieldVector (vector))
  (loop [x 0]
    (when (< x (.size nodeFields))
      (def nodeValue (.value (.get nodeFields x)))
      (def fieldVector (conj fieldVector {"labels" (vec (.labels (.asNode nodeValue)))} (.asMap nodeValue)))
      (recur (+ x 1))))
  fieldVector)


(defn getAllNodesParsed
  "Get Parsed Nodes"
  []
  (def plainNodes (getAllNodes))
  (def nodeVector (vector))
  (loop [x 0]
    (when (< x (.size plainNodes))
      (def nodeVector (conj nodeVector (parsePlainNode (.get plainNodes x))))
      (recur (+ x 1))))
  nodeVector)


(defn addStringToMapKeys
  "Adds a string to every key of a map
  Map keys should be strings."
  [stringMap string]
  (def stringMap2 stringMap)
  (def mapKeyVec (vec (keys stringMap)))
  (loop [x 0]
    (when (< x (count mapKeyVec))
      (def stringMap2 (clojure.set/rename-keys stringMap2 {(mapKeyVec x) (str (mapKeyVec x) string)}))
      (recur (+ x 1))))
  stringMap2)



(defn removeVectorStringSuffixes
  "Removes the string suffix from the Vector members"
  [mapKeyVector stringSuffix]
  (def suffixPatternS (str stringSuffix "$"))
  (def suffixPattern (java.util.regex.Pattern/compile suffixPatternS))
  (def retMapKeyVector [])
  (loop [x 0]
    (when (< x (count mapKeyVector))
      (def retMapKeyVector (conj retMapKeyVector (clojure.string/replace (mapKeyVector x) suffixPattern "")))
      (recur (+ x 1))))
  retMapKeyVector)



(defn createParameterPropertyString
  "Create Property String with parameter fields using map keys"
  [propertyMap & [characteristicString]]
  ;;The characteristicString is sometimes appended to map keys to distinguish
  ;;the keys when multiple maps and their keys are used in the same cypher
  ;;query with parameters
  (defn ifPropertyNonEmpty
    []
    (def propertyMapKeysVec (vec (keys propertyMap)))
    (def propertyString (str " "))
    (def psuedoMapKeysVec (vector))
    (if characteristicString
      (def psuedoMapKeysVec (removeVectorStringSuffixes propertyMapKeysVec characteristicString))
      (def psuedoMapKeysVec propertyMapKeysVec))
    (def propertyString (str "{"))
    ;;Concatenate propertyString with map keys as parameter keys and Node keys 
    (loop [x 0]
      (when (< x (count propertyMapKeysVec))
        (def propertyString (str propertyString " "  (str (psuedoMapKeysVec x)) " : {" (str (propertyMapKeysVec x)) "},"))
        (recur (+ x 1))))
    ;;Finalize propertyString by rmoving end comma and adding ' }'
    (def propertyString (str (apply str (drop-last propertyString)) " }")))
  (if (> (count (keys propertyMap)) 0)
    (ifPropertyNonEmpty)
    (def propertyString (str " ")))
  propertyString)


(defn combinePropertyMap
  "Combine PropertyMaps and associated propertyStrings.
  Name PropertyMaps appropriately.
  Input PropertyMaps as map of maps.
  Keys Should be strings"
  [propertyMaps]
  (def propertyStringMap {})
  (def combinedPropertyMap {})
  (defn mapPropertyString
    [mapKey]
    (def newPropertyMap (addStringToMapKeys (propertyMaps mapKey) mapKey))
    (def combinedPropertyMap (merge combinedPropertyMap newPropertyMap))
    (def propertyStringMap (assoc propertyStringMap mapKey (createParameterPropertyString newPropertyMap mapKey))))
  (dorun (map mapPropertyString (vec (keys propertyMaps))))
  [combinedPropertyMap propertyStringMap])


(defn createNewNode
  "Create a new node in the graph. Without any relationships.
  Node properties should be a clojure map.
  Map keys will be used as neo4j node keys.
  Map keys should be Strings only.
  Map values must be neo4j compatible Objects"
  [label propertyMap]
  (def neo4jDBDetails (getNeo4jDBDetails))
  (def driver (GraphDatabase/driver (get neo4jDBDetails 0) (AuthTokens/basic (get neo4jDBDetails 1) (get neo4jDBDetails 2))))
  (def session (.session driver))
  (def propertyMapKeysVec (vec (keys propertyMap)))
  (def propertyMap (createParameterPropertyString propertyMap))
  (def parameterMap (java.util.HashMap. propertyMap))
  (def statement (str "CREATE (node:" label " " propertyString " )"))
  (def stRes (.run session statement parameterMap))
  (def fullSummary (getFullSummary stRes))
  (.close driver)
  fullSummary)

(defn createRelation
  "Relate two nodes matched with their properties (input as clojure map) with it's own properties"
  [label1 propertyMap1 relationshipType relationshipPropertyMap label2 propertyMap2]
  (def neo4jDBDetails (getNeo4jDBDetails))
  (def driver (GraphDatabase/driver (get neo4jDBDetails 0) (AuthTokens/basic (get neo4jDBDetails 1) (get neo4jDBDetails 2))))
  (def session (.session driver))
  (def combinedProperties (combinePropertyMap {"1" propertyMap1 "2" propertyMap2 "R" relationshipPropertyMap}))
  (def cypherQuery (str "MATCH (node1:" label1 " " ((combinedProperties 1) "1") " ) , (node2:" label2 " " ((combinedProperties 1) "2") " ) CREATE (node1)-[:" relationshipType " " ((combinedProperties 1) "R") " ]->(node2)"))
  (def stRes(.run session cypherQuery (java.util.HashMap. (combinedProperties 0))))
  (def fullSummary (getFullSummary stRes))
  (.close driver)
  fullSummary)



(defn deleteDetachNodes
  "Delete node(s) matched using property map and detach (remove relationships)"
  [label propertyMap]
  (def neo4jDBDetails (getNeo4jDBDetails))
  (def driver (GraphDatabase/driver (get neo4jDBDetails 0) (AuthTokens/basic (get neo4jDBDetails 1) (get neo4jDBDetails 2))))
  (def session (.session driver))
  (def propertyString (createParameterPropertyString propertyMap))
  (def parameterMap (java.util.HashMap. propertyMap))
  (def stRes (.run session (str "MATCH (node:" label " " propertyString " ) DETACH DELETE node") parameterMap))
  (def fullSummary (getFullSummary stRes))
  (.close driver)
  fullSummary)



(defn deleteNodes
  "Delete node(s) matched using property map"
  [label propertyMap]
  (def neo4jDBDetails (getNeo4jDBDetails))
  (def driver (GraphDatabase/driver (get neo4jDBDetails 0) (AuthTokens/basic (get neo4jDBDetails 1) (get neo4jDBDetails 2))))
  (def session (.session driver))
  (def propertyString (createParameterPropertyString propertyMap))
  (def parameterMap (java.util.HashMap. propertyMap))
  (def stRes (.run session (str "MATCH (node:" label " " propertyString " ) DELETE node") parameterMap))
  (def fullSummary (getFullSummary stRes))
  (.close driver)
  fullSummary)

(defn createNodeEditString
  "Creates a node edit string.
  eg.., nodeName.prop1=val1 , nodeName.prop2=val2"
  [nodeName editPropertyMap & [characteristicString]]
  (defn ifPropertyNonEmpty
    []
    (def editPropertyMapKeysVec (vec (keys editPropertyMap)))
    (def editString (str " "))
    (def psuedoEditMapKeysVec (vector))
    (if characteristicString
      (def psuedoEditMapKeysVec (removeVectorStringSuffixes editPropertyMapKeysVec characteristicString))
      (def psuedoEditMapKeysVec editPropertyMapKeysVec))
    (def editString (str " " "SET" " "))
    (loop [x 0]
      (when (< x (count editPropertyMapKeysVec))
        ;;Similar to createParameterPropertyString
        (def editString (str editString " " nodeName "." (str (psuedoEditMapKeysVec x)) " = {" (str (editPropertyMapKeysVec x)) "} ,"))
        (recur (+ x 1))))
    ;;Finalize editString
    (def editString (str (apply str (drop-last editString)) " ")))
  (if (> (count (keys editPropertyMap)) 0)
    (ifPropertyNonEmpty)
    (def editString (str " ")))
  editString)

(defn editNodeProperties
  "Edit Properties of Node(s)"
  [label matchPropertyMap targetPropertyMap]
  (def neo4jDBDetails (getNeo4jDBDetails))
  (def driver (GraphDatabase/driver (get neo4jDBDetails 0) (AuthTokens/basic (get neo4jDBDetails 1) (get neo4jDBDetails 2))))
  (def session (.session driver))
  (def propertyMapM (addStringToMapKeys matchPropertyMap "M"))
  (def matchPropertyString (createParameterPropertyString propertyMapM "M"))
  (def targetPropertyMapE (addStringToMapKeys targetPropertyMap "E"))
  (def editString (createNodeEditString "node1" targetPropertyMapE "E"))
  (def cypherQuery (str "MATCH (node1:" label " " matchPropertyString " ) " editString))
  (def combinedPropertyMap (merge propertyMapM targetPropertyMapE))
  (def stRes (.run session cypherQuery (java.util.HashMap. combinedPropertyMap)))
  (def fullSumary (getFullSummary stRes))
  (.close driver)
  fullSummary)


(defn createNodeRemString
  "Creates a node property removal string.
  eg.., nodeName.prop1 , nodeName.prop2"
  [nodeName remPropertyVec]
  (def removeString (str "REMOVE "))
  (loop [x 0]
    (when (< x (count remPropertyVec))
      (def removeString (str removeString " " nodeName "." (str (remPropertyVec x)) ","))
      (recur (+ x 1))))
  (def removeString (str (apply str (drop-last removeString))))
  removeString)


(defn removeNodeProperties
  "Remove properties from Node"
  [label matchPropertyMap remPropertyVec]
  (def neo4jDBDetails (getNeo4jDBDetails))
  (def driver (GraphDatabase/driver (get neo4jDBDetails 0) (AuthTokens/basic (get neo4jDBDetails 1) (get neo4jDBDetails 2))))
  (def session (.session driver))
  (def removeString (createNodeRemString "node1" remPropertyVec))
  (def matchPropertyString (createParameterPropertyString matchPropertyMap))
  (def cypherQuery (str "MATCH (node1:" label " " matchPropertyString  " ) " removeString))
  (def stRes (.run session cypherQuery (java.util.HashMap. matchPropertyMap)))
  (def fullSummary (getFullSummary stRes))
  (.close driver)
  fullSummary)


(defn getNodes
  "Get Node(s) matched by label and propertyMap"
  [label propertyMap]
  (def neo4jDBDetails (getNeo4jDBDetails))
  (def driver (GraphDatabase/driver (get neo4jDBDetails 0) (AuthTokens/basic (get neo4jDBDetails 1) (get neo4jDBDetails 2))))
  (def session (.session driver))
  (def propertyString (createParameterPropertyString propertyMap))
  (def cypherQuery (str "MATCH (node:" label " " propertyString " ) RETURN node"))
  (def stList (.list (.run session cypherQuery (java.util.HashMap. propertyMap))))
  (.close driver)
  stList)

(defn getNodesParsed
  "Get Node(s) parsed matched by label and propertyMap"
  [label propertyMap]
  (def plainNodes (getNodes label propertyMap))
  (def nodeVector (vector))
  (loop [x 0]
    (when (< x (.size plainNodes))
      (def nodeVector (conj nodeVector (parsePlainNode (.get plainNodes x))))
      (recur (+ x 1))))
  nodeVector)


(defn customMatchQuery
  "Get Nodes by a custom parameterized Cypher query"
  [cypherQuery parameterMap]
  (def neo4jDBDetails (getNeo4jDBDetails))
  (def driver (GraphDatabase/driver (get neo4jDBDetails 0) (AuthTokens/basic (get neo4jDBDetails 1) (get neo4jDBDetails 2))))
  (def session (.session driver))
  (def stList (.list (.run session cypherQuery parameterMap)))
  (.close driver)
  stList)

(defn customMatchQueryParsed
  "Get Nodes by a custom parameterized Cypher query parsed"
  [cypherQuery parameterMap]
  (def plainNodes (customMatchQuery cypherQuery parameterMap))
  (def nodeVector (vector))
  (loop [x 0]
    (when (< x (.size plainNodes))
      (def nodeVector (conj nodeVector (parsePlainNode (.get plainNodes x))))
      (recur (+ x 1))))
  nodeVector)

(defn customUpdateQuery
  "Perform update by a parameterized Cypher query"
  [cypherQuery parameterMap]
  (def neo4jDBDetails (getNeo4jDBDetails))
  (def driver (GraphDatabase/driver (get neo4jDBDetails 0) (AuthTokens/basic (get neo4jDBDetails 1) (get neo4jDBDetails 2))))
  (def session (.session driver))
  (def stRes (.run session cypherQuery parameterMap))
  (def fullSummary (getFullSummary stRes))
  (.close driver)
  fullSummary)

;; Gnowsys specification functions start here


(defn createNT
  "Creates a Node Type"
  [nodeTypePropertyMap]
  (createNewNode "nodeType" nodeTypePropertyMap))

(defn createRT
  "Creates a Relationship type"
  [relTypePropertyMap]
  (createNewNode "relationType" relTypePropertyMap))

(defn createAT
  "Creates an Attribute type"
  [attTypePropertyMap]
  (createNewNode "attributeType" attTypePropertyMap))

(defn getNT
  "Get NodeTypes using propertyMap"
  [propertyMap]
  (getNodesParsed "nodeType" propertyMap))

(defn getRT
  "Get RelationTypes using propertyMap"
  [propertyMap]
  (getNodesParsed "relationType" propertyMap))

(defn getAT
  "Get RelationTypes using propertyMap"
  [propertyMap]
  (getNodesParsed "attributeType" propertyMap))

(defn create-nt-x
  "Creates a xType(XT) if it does not exist,
  and creates a relation between the NT and XT called hasNTX"
  [NTPropertyMap XTPropertyMap XRelType XLabel]
  (def ret "")
  (def combinedPropertyMaps (combinePropertyMap {"NT" NTPropertyMap "XT" XTPropertyMap}))
  (def existing-nt-x (customMatchQueryParsed (str "MATCH (NT:nodeType " ((combinedPropertyMaps 1) "NT")  " )-[:" XRelType "]-(XT:" XLabel " " ((combinedPropertyMaps 1) "XT") ") RETURN NT") (combinedPropertyMaps 0)))
  (if (= 0 (count existing-nt-x))
    (do ;;Either NT does'nt exist or XT does'nt exist or relation 'hasNTX' does'nt exist or all
      (def existingNT (getNT NTPropertyMap))
      (if (= 1 (count existingNT))
        (do ;;Unique NT exists. Proceed
          (def existingXT (getNodesParsed XLabel XTPropertyMap))
          (if (= 1 (count existingXT))
            (do ;;Unique XT exists. Proceed
              (def cypherQuery (str "MATCH (NT:nodeType " ((combinedPropertyMaps 1) "NT") " ), (XT:" XLabel " " ((combinedPropertyMaps 1) "XT")  " ) CREATE (NT)-[:" XRelType "]->(XT)"))
              (def ret (customUpdateQuery cypherQuery (combinedPropertyMaps 0))))
            (do ;;None or multiple XT exist
              (if (= 0 (count existingXT))
                (do ;;XT does'nt exist. Proceed
                  (def cypherQuery (str "MATCH (NT:nodeType " ((combinedPropertyMaps 1) "NT") ") CREATE (XT:" XLabel " " ((combinedPropertyMaps 1) "XT") " ) CREATE (NT)-[:" XRelType "]->(XT)"))
                  (def ret (customUpdateQuery cypherQuery (combinedPropertyMaps 0))))
                (do ;;Multiple XT exist
                  (def ret (str (count existingXT) " XT exist(s)")))))))
        (do ;;None or multiple NT exist
          (def ret (str (count existingNT) " NT exist(s)")))))
    (do ;;NT already 'hasNTX' XT
      (def ret "NT already 'hasNTX' XT")))
  ret)


(defn create-nt-relation
  "Creates a relationType(RT) if it does not exist,
  and creates a relation between the NT and RT called 'hasNTRelation'"
  [NTPropertyMap RTPropertyMap]
  (create-nt-x NTPropertyMap RTPropertyMap "hasNTRelation" "relationType"))

(defn create-nt-attribute
  "Creates an attributeType(AT) if it does not exist,
  and creates a relation between the NT and AT called 'hasNTAttribute'"
  [NTPropertyMap ATPropertyMap]
  (create-nt-x NTPropertyMap ATPropertyMap "hasNTAttribute" "attributeType"))
