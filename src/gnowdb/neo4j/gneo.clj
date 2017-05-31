(ns gnowdb.neo4j.gneo
  (:gen-class)
  (:require [clojure.set :as clojure.set]
            [clojure.java.io :as io]
            [clojure.string :as clojure.string]
            [gnowdb.neo4j.gdriver :as gdriver]))

(import '[org.neo4j.driver.v1 Driver AuthTokens GraphDatabase Record Session StatementResult Transaction Values]
        '[java.io PushbackReader])

(defn getNeo4jDBDetails
  "Get Neo4jDB Details :bolt-url,:username,:password"
  []
  (with-open [r (io/reader "src/gnowdb/neo4j/gconf.clj")]
              (read (PushbackReader. r))))

(defn getDriver
  "Get neo4j Database Driver"
  []
  (let [neo4jDBDetails (getNeo4jDBDetails)]
    (GraphDatabase/driver (neo4jDBDetails :bolt-url) (AuthTokens/basic (neo4jDBDetails :username) (neo4jDBDetails :password)))))

(defn parseRecordFields
	"Parse Record Fields(list of key value pairs)"
	[fieldList]
	(into
		{}
		(map
			(fn
				[field]
				[(str (.key field)) (.value field)]
			)
			fieldList
		)
	)
)


(defn parseStatementRecordList
	"Convert Record List to String clojure list"
	[recordList]
	(vec (map #(parseRecordFields (.fields %)) recordList))

)

(defn createSummaryMap
  "Creates a summary map from StatementResult object.
  This Object is returned by the run() method of session object
  To be used for cypher queries that dont return nodes
  Driver should not be closed before invoking this function"
  [statementResult]
  (let [summaryCounters (.counters (.consume statementResult))]
    {:constraintsAdded (.constraintsAdded summaryCounters) :constraintsRemoved (.constraintsRemoved summaryCounters) :containsUpdates (.containsUpdates summaryCounters) :indexesAdded (.indexesAdded summaryCounters) :indexesRemoved (.indexesRemoved summaryCounters) :labelsAdded (.labelsAdded summaryCounters) :labelsRemoved (.labelsRemoved summaryCounters) :nodesCreated (.nodesCreated summaryCounters) :nodesDeleted (.nodesDeleted summaryCounters) :propertiesSet (.propertiesSet summaryCounters) :relationshipsCreated (.relationshipsCreated summaryCounters) :relationshipsDeleted (.relationshipsDeleted summaryCounters)}))

(defn createSummaryString 
	"Creates Summary String only with only necessary information.
  	Takes summaryMap created by createSummaryMap function."
	[summaryMap]
	(reduce
		(fn [string k]
			(if(not= (k 1) 0)
				(str string (str (clojure.string/capitalize (subs (str (k 0)) 1 2)) (subs (str (k 0)) 2)) " :" (k 1) " ;")
				string
			)
		)
		""
		summaryMap
	)
)

(defn getFullSummary
  "Returns summaryMap and summaryString"
  [statementResult]
  (let [sumMap (createSummaryMap statementResult)]
    {:summaryMap sumMap :summaryString (createSummaryString sumMap)}))

(defn getCombinedFullSummary
  "Combine FullSummaries obtained from 'getFullSummary'"
	[fullSummaryVec]
	(let [ consolidatedMap 
			(apply merge-with
				(fn [& args]
					(if (some #(= (type %) java.lang.Boolean) args)
						(if (some identity args) true false)
						(apply + args)
					)
				)
				{:constraintsAdded 0 :constraintsRemoved 0 :containsUpdates false :indexesAdded 0 :indexesRemoved 0 :labelsAdded 0 :labelsRemoved 0 :nodesCreated 0 :nodesDeleted 0 :propertiesSet 0 :relationshipsCreated 0 :relationshipsDeleted 0}	
				(map #(% :summaryMap) fullSummaryVec)
			)
		]
		{:summaryMap consolidatedMap
		 :summaryString (createSummaryString consolidatedMap)
		}
	)
)

(defn getAllLabels
  "Get all the Labels from the graph, parsed."
  []
  (((gdriver/runQuery {:query "MATCH (n) RETURN DISTINCT LABELS(n)" :parameters {}}) :results) 0)
 )

(defn getAllNodes
  "Returns a lazy sequence of labels and properties of all nodes in the graph"
  []
  (map 
  	(fn 
  		[node]
  		{:labels (.labels (node "n")) :properties (.asMap (node "n"))}
  	)
  	(((gdriver/runQuery {:query "MATCH (n) RETURN n" :parameters {}}) :results) 0)
  )
 )

(defn getNodeKeys
  "Gets Node Keys as seq using NodeValue"
  [nodeValue]
  (iterator-seq (.iterator (.keys nodeValue))))

(defn addStringToMapKeys
  "Adds a string to every key of a map
  Map keys should be strings."
  [stringMap string]
  (let [stringMap2 (atom {}) mapKeyVec (vec (keys stringMap))]
    (doall (map (fn
                  [mapKey]
                  (swap! stringMap2 assoc (str mapKey string) (stringMap mapKey))) mapKeyVec))
    @stringMap2))

(defn removeVectorStringSuffixes
  "Removes the string suffix from the Vector members"
  [mapKeyVector stringSuffix]
  (let [suffixPattern (java.util.regex.Pattern/compile (str stringSuffix "$")) retMapKeyVector (atom [])]
    (doall (map (fn
                  [mapKey]
                  (swap! retMapKeyVector conj (clojure.string/replace mapKey suffixPattern ""))) mapKeyVector))
    @retMapKeyVector))

(defn createParameterPropertyString
  "Create Property String with parameter fields using map keys"
  [propertyMap & [characteristicString]]
  ;;The characteristicString is sometimes appended to map keys to distinguish
  ;;the keys when multiple maps and their keys are used in the same cypher
  ;;query with parameters
  (if (> (count (keys propertyMap)) 0)
    (let [propertyMapKeysVec (vec (keys propertyMap)) propertyString (atom "{") psuedoMapKeysVec (atom [])]
      (if characteristicString
        (reset! psuedoMapKeysVec (removeVectorStringSuffixes propertyMapKeysVec characteristicString))
        (reset! psuedoMapKeysVec propertyMapKeysVec))
      ;;Concatenate propertyString with map keys as parameter keys and Node keys
      (loop [x 0]
        (when (< x (count propertyMapKeysVec))
          (swap! propertyString str " " (str (@psuedoMapKeysVec x)) ":{" (str (propertyMapKeysVec x)) "},")
          (recur (+ x 1))))
      ;;Finalize propertyString by removing end comma and adding ' }'
      (reset! propertyString (str (apply str (drop-last @propertyString)) " }"))
      @propertyString)
    " "))

(defn combinePropertyMap
  "Combine PropertyMaps and associated propertyStrings.
  Name PropertyMaps appropriately.
  Input PropertyMaps as map of maps.
  Keys Should be strings"
  [propertyMaps]
  (let [propertyStringMap (atom {}) combinedPropertyMap (atom {})]
    (doall (map (fn
                  [mapKey]
                  (let [newPropertyMap (addStringToMapKeys (propertyMaps mapKey) mapKey)]
                    (swap! combinedPropertyMap merge newPropertyMap)
                    (swap! propertyStringMap assoc mapKey (createParameterPropertyString newPropertyMap mapKey)))) (vec (keys propertyMaps))))
    {:combinedPropertyMap @combinedPropertyMap :propertyStringMap @propertyStringMap}))

(defn createNewNode
  "Create a new node in the graph. Without any relationships.
  Node properties should be a clojure map.
  Map keys will be used as neo4j node keys.
  Map keys should be Strings only.
  Map values must be neo4j compatible Objects"
  [label propertyMap]
  ((gdriver/runQuery {:query (str "CREATE (node:" label " " (createParameterPropertyString propertyMap) " )") :parameters propertyMap}) :summary)
)

(defn createNewNode_tx
  "Create a new Node under a transaction."
  [label propertyMap tx]
  (getFullSummary (.run tx (str "CREATE (node:" label " " (createParameterPropertyString propertyMap) " )") (java.util.HashMap. propertyMap))))

(defn createRelation
  "Relate two nodes matched with their properties (input as clojure map) with it's own properties"
  [label1 propertyMap1 relationshipType relationshipPropertyMap label2 propertyMap2]
  (let [driver (getDriver) session (.session driver) combinedProperties (combinePropertyMap {"1" propertyMap1 "2" propertyMap2 "R" relationshipPropertyMap}) fullSummary (atom nil)]
    (reset! fullSummary (getFullSummary (.run session (str "MATCH (node1:" label1 " " ((combinedProperties :propertyStringMap) "1") " ) , (node2:" label2 " " ((combinedProperties :propertyStringMap) "2") " ) CREATE (node1)-[:" relationshipType " " ((combinedProperties :propertyStringMap) "R") " ]->(node2)") (java.util.HashMap. (combinedProperties :combinedPropertyMap)))))
    (.close driver)
    fullSummary))

(defn deleteDetachNodes
  "Delete node(s) matched using property map and detach (remove relationships)"
  [label propertyMap]
  ((gdriver/runQuery {:query (str "MATCH (node:" label " " (createParameterPropertyString propertyMap) " ) DETACH DELETE node") :parameters propertyMap}) :summary)
)

(defn deleteNodes
  "Delete node(s) matched using property map"
  [label propertyMap]
  ((gdriver/runQuery {:query (str "MATCH (node:" label " " (createParameterPropertyString propertyMap) " ) DELETE node") :parameters propertyMap}) :summary)
)

(defn createNodeEditString
  "Creates a node edit string.
  eg.., nodeName.prop1=val1 , nodeName.prop2=val2"
  [nodeName editPropertyMap & [characteristicString]]
  (if (> (count (keys editPropertyMap)) 0)
    (let [editPropertyMapKeysVec (vec (keys editPropertyMap)) editString (atom " ") psuedoEditMapKeysVec (atom [])]
      (if characteristicString
        (reset! psuedoEditMapKeysVec (removeVectorStringSuffixes editPropertyMapKeysVec characteristicString))
        (reset! psuedoEditMapKeysVec editPropertyMapKeysVec))
      (reset! editString " SET ")
      (loop [x 0]
        (when (< x (count editPropertyMapKeysVec))
          ;;Similar to createParameterPropertyString
          (swap! editString str " " nodeName "." (str (@psuedoEditMapKeysVec x)) " = {" (str (editPropertyMapKeysVec x)) "} ,")
          (recur (+ x 1))))
      ;;Finalize editString)
      (reset! editString (str (apply str (drop-last @editString)) " "))
      @editString)
    " "))

(defn editNodeProperties
  "Edit Properties of Node(s)"
  [label matchPropertyMap targetPropertyMap]
  (let [driver (getDriver) session (.session driver) fullSummary (atom nil) mPM (addStringToMapKeys matchPropertyMap "M") tPME (addStringToMapKeys targetPropertyMap "E")]
    (reset! fullSummary (getFullSummary (.run session (str "MATCH (node1:" label " " (createParameterPropertyString mPM "M") " ) " (createNodeEditString "node1" tPME "E")) (java.util.HashMap. (merge mPM tPME)))))
    (.close driver)
    @fullSummary))

(defn createNodeRemString
  "Creates a node property removal string.
  eg.., nodeName.prop1 , nodeName.prop2"
  [nodeName remPropertyVec]
  (str "REMOVE " (clojure.string/join ", " (doall (map (fn
                                                         [remProperty]
                                                         (str nodeName "." remProperty)) remPropertyVec)))))

(defn removeNodeProperties
  "Remove properties from Node"
  [label matchPropertyMap remPropertyVec]
  (let [driver (getDriver) session (.session driver) fullSummary (atom nil)]
    (reset! fullSummary (getFullSummary (.run session (str "MATCH (node1:" label " " (createParameterPropertyString matchPropertyMap) " ) " (createNodeRemString "node1" remPropertyVec)) (java.util.HashMap. matchPropertyMap))))
    (.close driver)
    @fullSummary))

(defn getNodes
  "Get Node(s) matched by label and propertyMap"
  [label propertyMap]
  (let [driver (getDriver) session (.session driver) stList (atom nil)]
    (reset! stList (.list (.run session (str "MATCH (node:" label " " (createParameterPropertyString propertyMap) ") RETURN node") (java.util.HashMap. propertyMap))))
    (.close driver)
    @stList))

(defn getNodesParsed
  "Get parsed Node(s) matched by label and propertyMap"
  [label propertyMap]
  (parsePlainNodes (getNodes label propertyMap)))

(defn customMatchQuery
  "Get Nodes by a custom parameterized Cypher query.
  paramterMap should be a clojure map"
  [cypherQuery parameterMap]
  (let [driver (getDriver) session (.session driver) stList (atom nil)]
    (reset! stList (.list (.run session cypherQuery (java.util.HashMap. parameterMap))))
    (.close driver)
    @stList))

(defn customMatchQueryParsed
  "Get Nodes by a custom parameterized Cypher query parsed"
  [cypherQuery parameterMap]
  (parsePlainNodes (customMatchQuery cypherQuery parameterMap)))

(defn customUpdateQuery
  "Perform update by a parameterized Cypher Query"
  [cypherQuery parameterMap]
  (let [driver (getDriver) session (.session driver) fullSummary (atom nil)]
    (reset! fullSummary (getFullSummary (.run session cypherQuery (java.util.HashMap. parameterMap))))
    (.close driver)
    @fullSummary))

;;Class building functions start here

(defn manageConstraints
  "Manage unique constraints or existance constraints.
  constraintType should be either UNIQUE or NODEEXISTANCE or RELATIONEXISTANCE or NODEKEY"
  [label CD propertyVec constraintType]
  (if (not (contains? #{"CREATE" "DROP"} CD))
    (throw (Exception. "CD Should be either CREATE or DROP"))
    (let [driver (getDriver) session (.session driver) tx (.beginTransaction session) summaries (atom [])]
      (try
        (reset! summaries (vec (doall (map (fn
                                             [property]
                                             (case constraintType
                                               "UNIQUE" (getFullSummary (.run tx (str CD " CONSTRAINT ON (label:" label ") ASSERT label." property " IS UNIQUE")))
                                               "NODEEXISTANCE" (getFullSummary (.run tx (str CD " CONSTRAINT ON (label:" label ") ASSERT exists(label." property ")")))
                                               "RELATIONEXISTANCE" (getFullSummary (.run tx (str CD " CONSTRAINT ON ()-[label:" label "]-() ASSERT exists(label." property ")")))
                                               "NODEKEY" (getFullSummary (.run tx (str CD " CONSTRAINT ON (label:" label ") ASSERT (" (clojure.string/join ", " (map (fn                                                                                            [prop]
                                                                                                                                                                       (str "label." prop)) property)) ") IS NODE KEY"))))) propertyVec))))
        (.success tx)
        (catch Exception E (do
                             (.failure tx)
                             (.printStackTrace E)
                             (.getMessage E)))
        (finally
          (.close tx))) (getCombinedFullSummary @summaries))))

(defn manageUniqueConstraints
  "Create/Drop Unique Constraints on label properties.
  label is treated as a Node Label.
  CD can be CREATE,DROP.
  propertyVec should be a vector of properties"
  [label CD propertyVec]
  (manageConstraints label CD propertyVec "UNIQUE"))

(defn manageExistanceConstraints
  "Create/Drop Existance Constraints on label properties.
  label is treated as a Node label or relation label based on value of NR.
  CD can be CREATE, DROP.
  propertyVec should be a vector of properties.
  NR should be either NODE or RELATION"
  [label CD propertyVec NR]
  (manageConstraints label CD propertyVec (str NR "EXISTANCE")))

(defn manageNodeKeyConstraints
  "Create/Drop Node Key Constraints on node label properties.
  label is treated as node label.
  CD can be CREATE, DROP.
  propertyVec should be a vector of vector of properties."
  [label CD propPropVec]
  ;;For some reason, creating/dropping a nodekey doesn't reflect on summary.
  ;;So don't be surprised if no errors occur or no changes exist in the summary.
  (manageConstraints label CD propPropVec "NODEKEY"))

(defn createNCConstraints
  "Create Constraints that apply to nodes with label NeoConstraint"
  []
  (manageNodeKeyConstraints "NeoConstraint" "CREATE" [["constraintType" "constraintTarget"]]))

(defn createCATConstraints
  "Create Constraints that apply to relationships with label NeoConstraintAppliesTo"
  []
  (manageExistanceConstraints "NeoConstraintAppliesTo" "CREATE" ["constraintValue"] "RELATION"))

(defn createNeoConstraint
  "Creates a NeoConstraint Node that describes a supported neo4j constraint.
  constraintType should be either of UNIQUE,EXISTANCE,NODEKEY.
  constraintTarget should be either of NODE,RELATION"
  [constraintType constraintTarget]
  (if (or (not (contains? #{"UNIQUE" "EXISTANCE" "NODEKEY"} constraintType)) (not (contains? #{"NODE" "RELATION"} constraintTarget)))
    (throw (Exception. "Incorrect Arguments. Read Doc"))
    (if (and (contains? #{"UNIQUE" "NODEKEY"} constraintType) (= "RELATION" constraintTarget))
      (throw (Exception. "UNIQUE/NODEKEY constraint can only be applied to Node Label"))
      (createNewNode "NeoConstraint" {"constraintType" constraintType "constraintTarget" constraintTarget}))))

(defn createAllNeoConstraints
  "Creates all NeoConstraints"
  []
  (getCombinedFullSummary (vec (doall (pcalls (fn [] (createNeoConstraint "UNIQUE" "NODE")) (fn [] (createNeoConstraint "NODEKEY" "NODE")) (fn [] (createNeoConstraint "EXISTANCE" "NODE")) (fn [] (createNeoConstraint "EXISTANCE" "RELATION")))))))

;;TODO CUSTOM CONSTRAINTS

(defn createATConstraints
  "Creates Constraints that apply to nodes with label AttributeType"
  []
  (manageNodeKeyConstraints "AttributeType" "CREATE" [["_name" "_datatype"]]))

(defn createAttributeType
  "Creates a node with Label AttributeType.
  _datatype should be a string of one of the following:
  'java.lang.Boolean', 'java.lang.Byte', 'java.lang.Short', 'java.lang.Integer', 'java.lang.Long', 'java.lang.Float', 'java.lang.Double', 'java.lang.Character', 'java.lang.String', 'java.util.ArrayList'"
  [_name _datatype]
  (if (not (contains? #{"java.lang.Boolean", "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long", "java.lang.Float", "java.lang.Double", "java.lang.Character", "java.lang.String","java.util.ArrayList"} _datatype))
    (throw (Exception. "Incorrect _datatype"))
    (let [driver (getDriver) session (.session driver)]
      (createNewNode "AttributeType" {"_name" _name "_datatype" _datatype}))))

(defn createClassConstraints
  "Create Constraints that apply to nodes with label Class"
  []
  (getCombinedFullSummary [(manageExistanceConstraints "Class" "CREATE" ["isAbstract" "classType"] "NODE")
  (manageNodeKeyConstraints "Class" "CREATE" [["className"]])]))

(defn getClassAttributeTypes
  "Get all AttributeTypes 'attributed' to a class"
  [className]
  (vec (doall (map (fn [at]
                     (at :properties)) (customMatchQueryParsed "MATCH (class:Class {className:{className}})-[rel:HasAttributeType]-(att:AttributeType) RETURN att" {"className" className})))))

(defn getClassNeoConstraints
  "Get all NeoConstraints attributed to a class"
  [className]
  (let [retVec (into [] (customMatchQuery "MATCH (class:Class {className:{className}})<-[ncat:NeoConstraintAppliesTo]-(neo:NeoConstraint) RETURN ncat,neo", {"className" className}))]
    (vec (doall (map (fn
                       [record]
                       (let [fieldsVec (into [] (.fields record)) retMap (atom {})]
                         (doall (map (fn
                                       [mmap]
                                       (swap! retMap merge mmap)) (vec (doall (map (fn
                                                                                     [field]
                                                                                     (into {} (.asMap (.value field)))) fieldsVec)))))
                         @retMap)) retVec)))))

(defn applyClassNeoConstraint
  "Apply a NeoConstraint that apply to a class.
  className: "
  [className _constraintType _constraintTarget _constraintValue]
  (let [CD "CREATE" constraintType (atom "") propertyVec [_constraintValue]]
    (reset! constraintType
            (case _constraintType
              ("UNIQUE" "NODEKEY") _constraintType
              "EXISTANCE" (str _constraintTarget _constraintType)))
    (manageConstraints className CD propertyVec @constraintType)))

(defn applyClassNeoConstraints
  "Apply all NeoConstraints for a class."
  [className]
  (getCombinedFullSummary (vec (doall (map (fn [classNeoConstraint] (applyClassNeoConstraint className (classNeoConstraint "constraintType") (classNeoConstraint "constraintTarget") (classNeoConstraint "constraintValue"))) (getClassNeoConstraints className))))))

(defn createClassSQ
  ;; WARNING!!!!!!!!!!!!!!! NOT BUG FREE ... YET ... BUT SINGLE QUERY.... SO TRANSACTIONAL, ie SOMEWHAT SAFE. 
  
  "Creates a node with label Class (Sequentially in a cypher query).
  isAbstract : true or false.
  className : unique string.
  classType : either 'NODE' or 'RELATION'.
  superClasses : vector of classNames.
  _attributeTypes : vector of maps with keys '_name', '_datatype'
  newAttributeTypes : same.
  propertyMap : optional propertyMap.
  _constraintsVec : vector of maps with keys 'constraintType', 'constraintTarget', 'constraintValue'."
  [isAbstract? className classType superClasses _attributeTypes &[propertyMap newAttributeTypes _constraintsVec]]
  (let [attributeTypes (atom []) constraintsVec (atom [])]
    (doall (map (fn
                  [_constraint]
                  (if (not= classType (_constraint "constraintTarget"))
                    (throw (Exception. (str "ConstraintTarget should be same as classType" _constraint))))) _constraintsVec))
    (doall (map (fn
                  [superClass]
                  (let [fetchedClass (getNodesParsed "Class" {"className" superClass}) c_attributeTypes (atom []) c_constraintsVec (atom [])]
                    (if (empty? fetchedClass)
                      (throw (Exception. (str "Class Does not Exist: " superClass))))
                    (if (not= classType (((fetchedClass 0) :properties) "classType"))
                      (throw (Exception. (str "Superclass should have same classType as new Class: " superClass)))
                      (do
                        (reset! c_attributeTypes (getClassAttributeTypes superClass))
                        (reset! c_constraintsVec (getClassNeoConstraints superClass))
                        (reset! attributeTypes (vec (distinct (concat @attributeTypes @c_attributeTypes))))
                        (reset! constraintsVec (vec (distinct (concat @constraintsVec @c_constraintsVec)))))))) superClasses))
    (reset! attributeTypes (vec (distinct (concat @attributeTypes _attributeTypes))))
    (reset! constraintsVec (vec (distinct (concat @constraintsVec _constraintsVec))))
    (let [driver (getDriver) session (.session driver) fullSummary (atom nil) constraintValues (vec (map (fn [constraint] {"constraintValue" (constraint "constraintValue")}) @constraintsVec)) subConstraintsVec (vec (map (fn [constraint] (dissoc constraint "constraintValue")) @constraintsVec)) combinedPropertyMap (combinePropertyMap (merge {"C" (merge {"isAbstract" isAbstract? "className" className "classType" classType} propertyMap)} (addStringToMapKeys (into {} (map-indexed vector @attributeTypes)) "AT") (addStringToMapKeys (into {} (map-indexed vector newAttributeTypes)) "NAT") (addStringToMapKeys (into {} (map-indexed vector subConstraintsVec)) "NEOC") (addStringToMapKeys (into {} (map-indexed vector constraintValues)) "NEOCV") (addStringToMapKeys (into {} (map-indexed vector ((fn [] (let [newMaps (atom [])] (doall (map (fn [superClass] (swap! newMaps conj {"className" superClass})) superClasses)) @newMaps))))) "SUP"))) cypherQuery (atom nil)]
      (if (or (not (contains? #{"NODE" "RELATION"} classType)) (not (= "java.lang.Boolean" (.getName (type isAbstract?)))))
        (throw (Exception. "classType or isAbstract? arguments dont conform to their standards. Read DOC")))
      (reset! cypherQuery (str (if (= 0 (count (vec (concat @attributeTypes superClasses @constraintsVec)))) "" " MATCH ") (clojure.string/join ", " (map (fn
                                                                                                                                                            [attribute]
                                                                                                                                                            (let [atindex (.indexOf @attributeTypes attribute)]
                                                                                                                                                              (str "(AT" atindex ":AttributeType " ((combinedPropertyMap :propertyStringMap) (str atindex "AT")) ") "))) @attributeTypes)) (if (or (= 0 (count @attributeTypes)) (= 0 (count superClasses))) "" ", ")
                               (clojure.string/join ", " (map (fn
                                                                [superClass]
                                                                (let
                                                                    [supindex (.indexOf superClasses superClass)]
                                                                  (str "(SUP" supindex ":Class " ((combinedPropertyMap :propertyStringMap) (str supindex "SUP")) ") "))) superClasses)) (if (= 0 (count @constraintsVec)) "" ", ")
                               (clojure.string/join ", " (map (fn
                                                                [constraint]
                                                                (let [neocindex (.indexOf @constraintsVec constraint)]
                                                                  (str "(NEOC" neocindex ":NeoConstraint " ((combinedPropertyMap :propertyStringMap) (str neocindex "NEOC")) ") "))) @constraintsVec))
                               "CREATE (newClass:Class " ((combinedPropertyMap :propertyStringMap) "C") ")" (if (= 0 (count superClasses)) "" ", ")
                               (clojure.string/join ", " (map (fn
                                                                [superClass]
                                                                (let [supindex (.indexOf superClasses superClass)]
                                                                  (str "(newClass)-[:IsSubClassOf]->(SUP" supindex ")"))) superClasses)) (if (= 0 (count newAttributeTypes)) "" ", ")
                               (clojure.string/join ", " (map (fn
                                                                [newAttribute]
                                                                (let [natindex (.indexOf newAttributeTypes newAttribute)]
                                                                  (str " (NAT" natindex ":AttributeType " ((combinedPropertyMap :propertyStringMap) (str natindex "NAT")) ") ")
                                                                  )) newAttributeTypes)) (if (= 0 (count @attributeTypes)) "" ", ")
                               (clojure.string/join ", " (map (fn
                                                                [attribute]
                                                                (let [atindex (.indexOf @attributeTypes attribute)]
                                                                  (str "(newClass)-[HAT" atindex ":HasAttributeType]->(AT" atindex") "))) @attributeTypes)) (if (= 0 (count newAttributeTypes)) "" ", ")
                               (clojure.string/join ", " (map (fn
                                                                [newAttribute]
                                                                (let [natindex (.indexOf newAttributeTypes newAttribute)]
                                                                  (str "(newClass)-[NHAT" natindex ":HasAttributeType]->(NAT" natindex ")"))) newAttributeTypes)) (if (= 0 (count @constraintsVec)) "" ", ")
                               (clojure.string/join ", " (map (fn
                                                                [constraint]
                                                                (let [neocindex (.indexOf @constraintsVec constraint)]
                                                                  (str "(NEOC" neocindex ")-[NEOCAT" neocindex ":NeoConstraintAppliesTo " ((combinedPropertyMap :propertyStringMap) (str neocindex "NEOCV")) "]->(newClass)"))) @constraintsVec))))
      (reset! fullSummary (getCombinedFullSummary [(getFullSummary (.run session @cypherQuery (combinedPropertyMap :combinedPropertyMap))) (applyClassNeoConstraints className)]))
      (.close driver)
      @fullSummary)))

(defn addClassAT_tx
  "Adds a relation HasAttributeType from Class to AttributeType under transaction.
  tx: neo4j bolt transaction object or session object
  _atname: _name of AttributeType.
  _atdatatype: _datatype of AttributeType.
  className: className of Class"
  [tx className _atname _atdatatype]
  (if (not= 1 (count (getNodesParsed "AttributeType" {"_name" _atname "_datatype" _atdatatype})))
    (throw (Exception. (str "Unique AttributeType with _name: " _atname ", _datatype:" _atdatatype " not found"))))
  (let [fullSummary (atom nil) combinedPropertyMap (combinePropertyMap {"C" {"className" className} "AT" {"_name" _atname "_datatype" _atdatatype}})]
    (reset! fullSummary (getFullSummary (.run tx (str "MATCH (class:Class " ((combinedPropertyMap :propertyStringMap) "C") ") , (att:AttributeType " ((combinedPropertyMap :propertyStringMap) "AT") ") CREATE (class)-[:HasAttributeType]->(att)") (combinedPropertyMap :combinedPropertyMap))))
    @fullSummary))

(defn addClassAT
  "Adds a relation HasAttributeType from Class to AttributeType.
  _atname: _name of AttributeType.
  _atdatatype: _datatype of AttributeType.
  className: className of Class"
  [className _atname _atdatatype]
  (let [driver (getDriver) fullSummary (atom nil)]
    (reset! fullSummary (addClassAT_tx (.session driver) className _atname _atdatatype))
    (.close driver)
    @fullSummary))

(defn addClassNC_tx
  "Adds a relation NeoConstraintAppliesTo from Class to NeoConstraint under transaction.
  tx: neo4j bolt transaction object or session object.
  constraintType should be either of UNIQUE,EXISTANCE,NODEKEY.
  constraintTarget should be either of NODE,RELATION.
  constraintValue should be the AttributeType"
  [tx className _constraintType _constraintTarget _constraintValue]
  (let [fetchedClass (getNodesParsed "Class" {"className" className "classType" _constraintTarget}) fullSummary (atom nil) combinedPropertyMap (combinePropertyMap {"C" {"className" className} "NEOC" {"constraintType" _constraintType  "constraintTarget" _constraintTarget} "CV" {"constraintValue" _constraintValue}})]
    (if (not= 1 (count fetchedClass))
      (throw (Exception. (str "Unique Class with className:" className ", and classType:" _constraintTarget " not found"))))
    (reset! fullSummary (getFullSummary (.run tx (str "MATCH (class:Class " ((combinedPropertyMap :propertyStringMap) "C") ") , (neoc:NeoConstraint " ((combinedPropertyMap :propertyStringMap) "NEOC") ") CREATE (neoc)-[ncat:NeoConstraintAppliesTo " ((combinedPropertyMap :propertyStringMap) "CV") "]->(class)") (combinedPropertyMap :combinedPropertyMap))))
    @fullSummary))

(defn addClassNC
  "Adds a relation NeoConstraintAppliesTo from Class to NeoConstraint.
  constraintType should be either of UNIQUE,EXISTANCE,NODEKEY.
  constraintTarget should be either of NODE,RELATION.
  constraintValue should be the AttributeType"
  [className _constraintType _constraintTarget _constraintValue]
  (let [driver (getDriver) fullSummary (atom nil)]
    (reset! fullSummary (addClassNC_tx (.session driver) className _constraintType _constraintTarget _constraintValue))
    (.close driver)
    @fullSummary))

(defn addClassSup_tx
  "Adds a relation IsSubClassOf from one Class to another under transaction.
  tx: neo4j bolt transaction object or session object.
  className: className of subClass.
  supClassName: className of supClass"
  [tx className supClassName]
  (let [fullSummary (atom nil) combinedPropertyMap (combinePropertyMap {"C" {"className" className} "SUP" {"className" supClassName}})]
    (reset! fullSummary (getFullSummary (.run tx (str "MATCH (class:Class " ((combinedPropertyMap :propertyStringMap) "C") ") , (supClass:Class " ((combinedPropertyMap :propertyStringMap) "SUP") ") CREATE (class)-[:IsSubClassOf]->(supClass)") (combinedPropertyMap :combinedPropertyMap))))
    @fullSummary))

(defn addClassSup
  "Adds a relation IsSubClassOf from one Class to another.
  className: className of subClass.
  supClassName: className of supClass"
  [className supClassName]
  (let [driver (getDriver) fullSummary (atom nil)]
    (reset! fullSummary (addClassSup_tx (.session driver)  className supClassName))
    (.close driver)
    @fullSummary))

;; For Some reason, when using transactions, the following function hangs. Set transactional? to true and uncomment to reproduce the error. Fair warning: DB becomes unusable.

(defn createClassFN
  "Creates a node with label Class (Functionally).
  transactional? : true or false, depending on whether creation should take place under a transaction or not
  isAbstract : true or false.
  className : unique string.
  classType : either 'NODE' or 'RELATION'.
  superClasses : vector of classNames.
  _attributeTypes : vector of maps with keys '_name', '_datatype'.
  propertyMap : optional propertyMap.
  _constraintsVec : vector of maps with keys 'constraintType', 'constraintTarget', 'constraintValue'."
  [transactional? isAbstract? className classType superClasses _attributeTypes propertyMap _constraintsVec]
  (let [attributeTypes (atom []) constraintsVec (atom [])]
    (if (or (not (contains? #{"NODE" "RELATION"} classType)) (not (= "java.lang.Boolean" (.getName (type isAbstract?)))))
        (throw (Exception. "classType or isAbstract? arguments dont conform to their standards. Read DOC")))
    (doall (map (fn
                  [superClass]
                  (let [fetchedClass (getNodesParsed "Class" {"className" superClass})]
                    (if (empty? fetchedClass)
                      (throw (Exception. (str "Class Does not Exist: " superClass))))
                    (if (not= classType (((fetchedClass 0) :properties) "classType"))
                      (throw (Exception. (str "Superclass should have same classType as new Class: " superClass))))
                    (reset! attributeTypes (vec (distinct (concat @attributeTypes (getClassAttributeTypes superClass)))))
                    (reset! constraintsVec (vec (distinct (concat @constraintsVec (getClassNeoConstraints superClass))))))) superClasses))
    (reset! attributeTypes (vec (distinct (concat @attributeTypes _attributeTypes))))
    (reset! constraintsVec (vec (distinct (concat @constraintsVec _constraintsVec))))
    (getCombinedFullSummary [(if transactional?
      (let [driver (getDriver) session (.session driver) trx (.beginTransaction session) summaries (atom [])]
        (try
          ;; (swap! summaries conj (createNewNode_tx "Class" (merge {"className" className "classType" classType "isAbstract" isAbstract?} propertyMap) trx))
          ;; (println "Class Created")
          ;; (doall (map (fn [attributeType] (swap! summaries conj (addClassAT_tx trx className (attributeType "_name") (attributeType "_datatype")))) @attributeTypes))
          ;; (println "AttributeTypes Created")
          ;; (doall (map (fn [constraint] (swap! summaries conj (addClassNC_tx trx className (constraint "constraintType") (constraint "constraintTarget") (constraint "constraintValue")))) @constraintsVec))
          ;; (println "NeoConstraints Created")
          ;; (doall (map (fn [superClass] (swap! summaries conj (addClassSup_tx trx className superClass))) superClasses))
          ;; (println "SuperClasses Added")
          (.success trx)
          (catch Exception E (do
                               (.failure trx)
                               (.printStackTrace E)
                               (.getMessage E)))
          (finally (do
                     (.close trx)
                     (.close session)
                     (.close driver))))
      	(getCombinedFullSummary @summaries))
      (getCombinedFullSummary (vec (concat [(createNewNode "Class" (merge {"className" className "classType" classType "isAbstract" isAbstract?}))] (vec (doall (map getCombinedFullSummary (vec (doall (pcalls (fn [] (vec (doall (pmap (fn [attributeType] (addClassAT className (attributeType "_name") (attributeType "_datatype"))) @attributeTypes)))) (fn [] (vec (doall (pmap (fn [constraint] (addClassNC className (constraint "constraintType") (constraint "constraintTarget") (constraint "constraintValue"))) @constraintsVec)))) (fn [] (vec (doall (pmap (fn [superClass] (addClassSup className superClass)) superClasses)))))))))))))) (applyClassNeoConstraints className)])))

(defn gnowdbInit
  "Create Initial constraints"
  []
  (getCombinedFullSummary [(createNCConstraints)
                           (createATConstraints)
                           (createCATConstraints)
                           (createClassConstraints)
                           (createAllNeoConstraints)]))

(defn validatePropertyMap
  "Validates a propertyMap for a class with className.
  Assumes class with given className exists"
  [className propertyMap]
  (let [classAttributeTypes (getClassAttributeTypes className) errors (atom [])]
    (if (> (count (keys propertyMap)) (count classAttributeTypes))
      (swap! errors conj  (str "No of properties (" (count (keys propertyMap)) ") > No of AttributeTypes (" (count classAttributeTypes) ")")))
    (doall (pmap (fn
                   [property]
                   (if (not= 1 (count (filter (fn [classAttributeType] (= classAttributeType {"_name" property "_datatype" (.getName (type (propertyMap property)))})) classAttributeTypes)))
                     (swap! errors conj (str "Unique AttributeType _name : " property ", _datatype : " (.getName (type (propertyMap property))) " not found for Class : " className)))) (keys propertyMap)))
    @errors))

(defn createNodeInstance
  "Creates a node , as an instance of a class with classType:NODE."
  [className propertyMap]
  (let [nodeClass (getNodesParsed "Class" {"className" className "classType" "NODE"})]
    (if (not= 1 (count nodeClass))
      (throw (Exception. (str "Unique Node Class with className:" className " ,classType:NODE doesn't exist")))
      (if (((nodeClass 0) :properties) "isAbstract")
        (throw (Exception. (str className " is Abstract"))))))
  (let [propertyErrors (validatePropertyMap className propertyMap)]
    (if (not= 0 (count propertyErrors))
      (throw (Exception. (str "PropertyMap is not valid : " propertyErrors)))))
  (createNewNode className propertyMap))

(defn createRelationInstance
  "Creates a relation between two nodes, as an instance of a class with classType:RELATION.
  fromClassName: className of 'out' label.
  fromPropertyMap: a property map that matches one or more 'out' nodes.
  propertyMap: relation propertyMap.
  toClassName: className of 'in' label.
  toPropertyMap: a property map that matches one or more 'in' nodes."
  [className fromClassName fromPropertyMap propertyMap toClassName toPropertyMap]
  (let [relClass (getNodesParsed "Class" {"className" className "classType" "RELATION"})]
    (if (not= 1 (count relClass))
      (throw (Exception. (str "Unique Relation Class with className:" className " ,classType:RELATION doesn't exist")))
      (if (((relClass 0) :properties) "isAbstract")
        (throw (Exception. (str className " is Abstract"))))))
  (if (not= 1 (count (getNodesParsed "Class" {"className" fromClassName "classType" "NODE"})))
    (throw (Exception. (str "Unique Node Class with className:" fromClassName " ,classType:NODE doesn't exist"))))
  (if (not= 1 (count (getNodesParsed "Class" {"className" toClassName "classType" "NODE"})))
    (throw (Exception. (str "Unique Node Class with className:" toClassName " ,classType:NODE doesn't exist"))))
  (let [propertyErrors (validatePropertyMap className propertyMap)]
    (if (not= 0 (count propertyErrors))
      (throw (Exception. (str "PropertyMap is not valid : " propertyErrors)))))
  (createRelation fromClassName fromPropertyMap className propertyMap toClassName toPropertyMap))
