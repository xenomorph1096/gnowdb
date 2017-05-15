(ns gnowdb.orientdb
  (:require [clojure.string :as clojure.string]
            [clojure.data.json :as clojure.data.json]
            [clojure.set :as clojure.set]))
(import [com.tinkerpop.blueprints.impls.orient OrientGraphFactory OrientGraph OrientBaseGraph OrientVertex OrientEdge OrientVertexType OrientEdgeType]
        [com.orientechnologies.orient.core.sql OCommandSQL]
        [com.orientechnologies.orient.core.command.script OCommandScript]
        [com.tinkerpop.blueprints Direction])

(defn contains-value? [coll element]
  (boolean (some #(= element %) coll)))

(defn getOrientDBDetails
  "Get OrientDB Connection info"
  []
  ["remote:localhost/GnowDB" "root" "root"])

(defn connect
  "Connect to orientdb and get OrientGraphFactory Object"
  []
  (let [orientDBDetails (getOrientDBDetails)]
    (OrientGraphFactory. (orientDBDetails 0) (orientDBDetails 1) (orientDBDetails 2))))

(defn execCommand
  "Executes SQL Command on OrientGraph Object"
  [^OrientGraph orientGraph
   ^String sqlCommand]
  (.execute (.command orientGraph (OCommandSQL. sqlCommand)) nil))

(defn execBatch
  "Executes SQL Batch on OrientGraph Object"
  [^OrientGraph orientGraph
   ^String sqlBatch]
  (.execute (.command orientGraph (OCommandScript. "sql" sqlBatch)) nil))

;; (defn getVertices
;;   "Get All Vertices from OrientGraphFactory object.
;;   Returns an Iterable"
;;   [^OrientGraphFactory orientDBGraphFactoryObject]
;;   (try
;;     (def orientGraph (.getNoTx orientDBGraphFactoryObject))
;;     (def vertices (.getVertices orientGraph))
;;     (catch Exception E (.toString E))
;;     (finally (.shutdown orientGraph)))
;;   vertices)

;; (defn createDCProperties
;;   "Create Dublin Core Metadata Properties for Class"
;;   [^OrientGraph orientGraph
;;    ^String className]
;;   (def DCProperties [{:name "Title" :type "STRING"} {:name "Creator" :type "STRING"} {:name "Subject" :type "STRING"} {:name "Description" :type "STRING"} {:name "Publisher" :type "STRING"} {:name "Contributor" :type "STRING"} {:name "Date" :type "DATE"} {:name "Type" :type "STRING"} {:name "Format" :type "STRING"} {:name "Identifier" :type "STRING"} {:name "Source" :type "STRING"} {:name "Language" :type "STRING"} {:name "Relation" :type "STRING"} {:name "Coverage" :type "STRING"} {:name "Rights" :type "STRING"}])
;;   (doall (map (fn [DCP]
;;                 (execCommand orientGraph (str "CREATE PROPERTY " className "." (DCP :name) " IF NOT EXISTS " (DCP :type)))) DCProperties)))

;; (defn createDCGenClass
;;   "Create Generic Abstract DC Class with optional Dublin Core Metadata elements"
;;   []
;;   (def orientGF (connect))
;;   (def orientGraph (.getTx orientGF))
;;   (try
;;     (execCommand orientGraph "CREATE CLASS DCGen IF NOT EXISTS ABSTRACT")
;;     (createDCProperties orientGraph "DCGen")
;;     (catch Exception E
;;       (do
;;         (.printStackTrace E)
;;         (.getMessage E)))
;;     (finally (.shutdown orientGraph))))


(defn createClass
  ""
  [^String className
   ^String superClass
   ^Boolean isAbstract]
  (let [orientGraph (.getTx (connect))]
    (try
      (execCommand orientGraph (str "CREATE CLASS " className " IF NOT EXISTS " (if (= superClass "") "" (str "EXTENDS " superClass " ")) (if isAbstract " ABSTRACT" "")))
      (catch Exception E (do
                           (.printStackTrace E)
                           (.getMessage E)
                           (.rollback orientGraph)))
      (finally (.shutdown orientGraph)))))

(defn getClassCustomFields
  "Gets the customFields of a class"
  [className]
  (let [orientGraph (.getNoTx (connect))]
    (try
      ((into {} (.getProperties ((vec (iterator-seq (.iterator (execCommand orientGraph (str "SELECT customFields FROM (SELECT expand(classes) FROM metadata:schema) WHERE name='" className "'"))))) 0))) "customFields")
      (catch Exception E (do
                           (.printStackTrace E)
                           (.getMessage E)))
      (finally (.shutdown orientGraph)))))

(defn setClassCustomField
  "Sets a customField of a class"
  [className fieldKey fieldVal]
  (let [orientGraph (.getTx (connect))]
    (try
      (execCommand orientGraph (str "ALTER CLASS " className " CUSTOM " fieldKey "='" fieldVal "'"))
      (catch Exception E (do
                           (.printStackTrace E)
                           (.getMessage E)
                           (.rollback orientGraph)))
      (finally (.shutdown orientGraph)))))

(defn createPropertyString
  "Create a String that describes an object with certain properties.
  To be used in a SQL Query.
  propertiesVec should be a vector of maps with the following keys :
  pname : a STRING
  ptype : a STRING, either of NUMERIC, BOOLEAN, STRING, EMBEDDEDLIST
  pval : vector of strings if EMBEDDEDLIST or a STRING otherwise"
  [propertiesVec]
  (clojure.string/join ", " (doall (map (fn [property]
                (str (property :pname) " = " (let [ptype (property :ptype)]
                                               (case ptype
                                                 ("BOOLEAN" "NUMERIC") (property :pval)
                                                 "STRING" (str "\"" (property :pval) "\"")
                                                 "EMBEDDEDLIST" (str "\"" (clojure.string/join ", " (property :pval)) "\""))))) propertiesVec))))

(defn createConstraintString
  "Create Constraint String."
  [pconstraints]
  (if (empty? pconstraints) "" (str "(" (clojure.string/join ", " (vec (map (fn
         [pconstraint]
         (str (pconstraint :cname) " " (if (= (pconstraint :ctype) "BOOLEAN") (pconstraint :cval) (str "\"" (pconstraint :cval) "\"")))) pconstraints))) ")")))

(defn makeUniqIndexName
  "Create an index name that hopes to be unique."
  [className properties]
  (str className "_" (clojure.string/join "_" properties) ".UNIQUE"))

(defn makePropertyListUnique
  "Creates a composite Index on a list of properties(as a vector) of a class to make them compositely unique.
  Index Name should be unique.
  Class and properties should already exist"
  [indexName className propertyListVec]
  (let [orientGraph (.getTx (connect))]
    (try
      (execCommand orientGraph (str "CREATE INDEX " indexName " ON " className "(" (clojure.string/join ", " propertyListVec) ") UNIQUE"))
      (catch Exception E (do
                           (.printStackTrace E)
                           (.getMessage E)
                           (.rollback orientGraph)))
      (finally (.shutdown orientGraph)))))

(defn createProperty
  "Create Property for a class."
  [className,propertyMap,orientGraph]
  (execCommand orientGraph (str "CREATE PROPERTY " className "." (propertyMap :pname) " IF NOT EXISTS " (propertyMap :pdatatype) " " (createConstraintString (propertyMap :pconstraints)))))

(defn createProperties
  "Create properties and their constraints to an existing class.
  Properties should be given as Vector of Maps.
  Each Map is a property with mandatory keys:':pname',':pdatatype',':pconstraints'.
  ':pname' : String
  ':pdatatype' : One of orientdb datatypes listed here: 'https://orientdb.com/docs/2.2/Types.html'
  ':pconstraints' : A vector (can be empty) of maps with keys: ':cname', ':ctype', ':cval'
                   :cname can be one of : 'MIN', 'MAX', 'MANDATORY', 'READONLY', 'NOTNULL', 'REGEXP'
                   :ctype can be one of : 'STRING', 'BOOLEAN'.
                   :cval takes a value according to choice of :cname and :ctype
  'pUniques' : A vector (can be empty) of Maps with the following keys : ':propertyListVec'"
  [className,propertyVec pUniques]
  (try
    (doall (map (fn [propertyMap]
                   (let [orientGraph (.getNoTx (connect))]
                     (createProperty className propertyMap orientGraph))) propertyVec))
    (doall (pmap (fn [pUnique]
                   (let [orientGraph (.getNoTx (connect))]
                     (makePropertyListUnique (makeUniqIndexName className (pUnique :propertyListVec)) className (pUnique :propertyListVec)))) pUniques))
    (catch Exception E (do
                         (.printStackTrace E)
                         (.getMessage E)))))

(defn createATClass
  "Create AttributeType Vertex Class"
  []
  (createClass "AttributeType" "V" false)
  (createProperties "AttributeType" [{:pname "_name" :pdatatype "STRING" :pconstraints [{:cname "MANDATORY" :ctype "BOOLEAN" :cval "TRUE"} {:cname "MIN" :ctype "STRING" :cval "1"} {:cname "NOTNULL" :ctype "BOOLEAN" :cval "TRUE"}]} {:pname "_datatype" :pdatatype "STRING" :pconstraints [{:cname "MANDATORY" :ctype "BOOLEAN" :cval "TRUE"} {:cname "MIN" :ctype "STRING" :cval "1"} {:cname "NOTNULL" :ctype "BOOLEAN" :cval "TRUE"}]} {:pname "_subjectTypes" :pdatatype "EMBEDDEDLIST" :pconstraints []}] [{:propertyListVec ["_name"]}]))

(defn createVCClass
  "Create ValueConstraint Vertex Class"
  []
  (createClass "ValueConstraint" "V" false)
  (createProperties "ValueConstraint" [{:pname "_cname" :pdatatype "STRING" :pconstraints [{:cname "MANDATORY" :ctype "BOOLEAN" :cval "TRUE"} {:cname "MIN" :ctype "STRING" :cval "1"} {:cname "NOTNULL" :ctype "BOOLEAN" :cval "TRUE"}]} {:pname "_ctype" :pdatatype "STRING" :pconstraints [{:cname "MANDATORY" :ctype "BOOLEAN" :cval "TRUE"} {:cname "MIN" :ctype "STRING" :cval "1"} {:cname "NOTNULL" :ctype "BOOLEAN" :cval "TRUE"}]}] [{:propertyListVec ["_cname"]}]))

(defn createVC
  "Create ValueConstraint"
  [_cname _ctype]
  (let [orientGraph (.getTx (connect))]
    (try
      (.addVertex orientGraph "class:ValueConstraint" (into-array ["_cname" _cname "_ctype" _ctype]))
      (.commit orientGraph)
      (catch Exception E (do
                           (.printStackTrace E)
                           (.getMessage E)
                           (.rollback orientGraph)))
      (finally (.shutdown orientGraph)))))

(defn createAllVC
  "Create all the ValueConstraints"
  []
  (doall (pmap (fn [VC]
                 (createVC (VC :cname) (VC :ctype))) [{:cname "MIN" :ctype "STRING"} {:cname "MAX" :ctype "STRING"} {:cname "REGEX" :ctype "STRING"} {:cname "MANDATORY" :ctype "BOOLEAN"} {:cname "NOTNULL" :ctype "BOOLEAN"} {:cname "READONLY" :ctype "BOOLEAN"}])))

(defn createCATClass
  "Create ConstraintAppliesTo Edge Class"
  []
  (createClass "ConstraintAppliesTo" "E" false)
  (createProperties "ConstraintAppliesTo" [{:pname "_cval" :pdatatype "STRING" :pconstraints [{:cname "MANDATORY" :ctype "BOOLEAN" :cval "TRUE"} {:cname "MIN" :ctype "STRING" :cval "1"} {:cname "NOTNULL" :ctype "BOOLEAN" :cval "TRUE"}]}] []))

(defn createEdge
  "Create an edge between two vertices.
  Vertices Are represented using @rid.
  ridOut, ridIn are strings of the form #21:12 etc.
  edgePropertyVec will be used to create a property string using createPropertyString"
  [ridOut ridIn edgeClassName edgePropertyVec]
  (let [orientGraph (.getTx (connect))]
    (try
      (execCommand orientGraph (str "CREATE EDGE " edgeClassName " FROM " ridOut " TO " ridIn " SET " (createPropertyString edgePropertyVec)))
      (catch Exception E (do
                           (.printStackTrace E)
                           (.getMessage E)
                           (.rollback orientGraph)))
      (finally (.shutdown orientGraph)))))

(defn createAT
  "Create an AttributeType.
  _subjectTypes should be a vector of NodeTypes.., eg ['NT1' 'NT2'].
  constraintsVec should be a vector of maps with keys: ':cname',':cval'
  ':cname' is the _cname value of desired ValueConstraint Vertex.
  ':cval' is the value of the constraint"
  [_name _datatype _subjectTypes constraintsVec]
  (let [orientGraph (.getNoTx (connect))]
    (try
      (if (= 0 (execBatch orientGraph (let [sqlBatch
                                            (atom (str "BEGIN\n"
                                                       "LET ATVertex = CREATE VERTEX AttributeType SET " (createPropertyString [{:pname "_name" :ptype "STRING" :pval _name} {:pname "_datatype" :ptype "STRING" :pval _datatype} {:pname "_subjectTypes" :ptype "EMBEDDEDLIST" :pval _subjectTypes}]) " \n"))]
                                        (swap! sqlBatch str (clojure.string/join "\n" (doall (map (fn
                                                                                                    [VC]
                                                                                                    (str "LET VCVertex = SELECT FROM ValueConstraint WHERE _cname = '" (VC :cname) "'\n"
                                                                                                         "if($VCVertex.size()!=1){\n"
                                                                                                         "ROLLBACK\n"
                                                                                                         "RETURN 0\n"
                                                                                                         "}\n"
                                                                                                         "CREATE EDGE ConstraintAppliesTo FROM $VCVertex TO $ATVertex SET _cval = '" (VC :cval) "'\n")) constraintsVec))) "COMMIT\nRETURN 1\n") @sqlBatch))) (throw (Exception. "Errors occured while creating ValueConstraint for AttributeType. Do unique ValueConstraints exist ?")) 1)
      (catch Exception E
        (do
          (.printStackTrace E)
          (.getMessage E)))
      (finally (.shutdown orientGraph)))))

(defn createATs
  "Create Multiple AttributeTypes.
  ATInput should be a vector of maps with keys: ':name', ':datatype', ':subjectTypes', ':constraintsVec'"
  [ATInput]
  (doall (pmap (fn
         [ATI]
                 (createAT (ATI :name) (ATI :datatype) (ATI :subjectTypes) (ATI :constraintsVec))) ATInput)))

(defn parseAT
  "Parses a vector ID (.toString ORecordId) of Vertex Class AttributeType.
  Returns a map of all properties of AT including constraints by parsing ConstraintAppliesTo Edges 'IN' to the AT."
  [ATVertexId orientGraph]
  (let [ATV (atom {})]
    (try
      (let [ATVertex (.getVertex orientGraph ATVertexId) CATEdges (vec (iterator-seq (.iterator (.getEdges ATVertex Direction/IN (into-array ["ConstraintAppliesTo"])))))]
        (reset! ATV (into {} (.getProperties  ATVertex)))
        (swap! ATV assoc "_subjectTypes" (into [] (@ATV "_subjectTypes")) :constraints (vec (doall (map (fn
                                                                                                          [CATE]
                                                                                                          (merge (into {} (.getProperties (.getVertex orientGraph (.getOutVertex CATE)))) (.getProperties CATE))) CATEdges))))
        @ATV)
      (catch Exception E (do
                           (.printStackTrace E)
                           (.getMessage E)
                           (.getCause E))))))

(defn parseATs
  "Parse a list(vector) of @rids of AttributeTypes"
  [ATList orientGraph]
  (vec (doall (map (fn
                     [AT]
                     (parseAT AT orientGraph)) ATList))))

(defn getATs
  "Get all Vertices of class AttributeType"
  []
  (let [orientGraph (.getNoTx (connect)) ATVs (atom (vec (iterator-seq (.iterator (.getVerticesOfClass orientGraph "AttributeType")))))]
    (reset! ATVs (vec (doall (map (fn
                                    [ATV]
                                    (.toString (.getId ATV))) @ATVs))))
    (swap! ATVs parseATs orientGraph)
    @ATVs))

(defn addXTAttribute
  "Add a Property using existing AttributeType to a NodeType/RelationType"
  [className _name]
  (let [orientGraph (.getTx (connect)) ATVertices (atom [])]
    (reset! ATVertices (.getVertices orientGraph "AttributeType" (into-array ["_name"]) (into-array [_name])))
    (try
      (let [AT (atom (parseATs (map (fn
                                      [ATVertex]
                                      (.getId ATVertex)) (vec (iterator-seq (.iterator @ATVertices)))) orientGraph))]
        (if (not= 1 (count @AT))
          (throw (Exception. (str "Unique AttributeType not Found : " _name))))
        (swap! AT (fn
                    [at]
                    (let [newAT (atom (at 0))]
                      (swap! newAT clojure.set/rename-keys {"_subjectTypes" :subjectTypes "_name" :pname "_datatype" :pdatatype :constraints :pconstraints})
                      (swap! newAT assoc :pconstraints (vec (doall (map (fn
                                                                          [pconstraint]
                                                                          (clojure.set/rename-keys pconstraint {"_cname" :cname "_ctype" :ctype "_cval" :cval})) (@newAT :pconstraints)))))
                      @newAT)))
        (if (not (contains-value? (@AT :subjectTypes) className))
          (.setProperty ((vec (iterator-seq (.iterator @ATVertices))) 0) "_subjectTypes" (java.util.ArrayList. (conj (@AT :subjectTypes) className))))
        (createProperty className @AT orientGraph)
        (.commit orientGraph))
      (catch Exception E (do
                           (.printStackTrace E)
                           (.getMessage E)
                           (.rollback orientGraph)))
      (finally (.shutdown orientGraph)))))

(defn addXTAttributes
  "Add Properties using AttributeTypes to a NodeType/RelationType.
  attributeTypesVec should be a vector of strings. Each string should match '_name' property of an existing AttributeType.
  newAttributeTypes should be a vector."
  [className attributeTypesVecI & [newAttributeTypes pUniques]]
  (try
    (createATs newAttributeTypes)
    (let [attributeTypesVec (vec (concat attributeTypesVecI (vec (doall (map (fn
                                                                       [newAT]
                                                                       (newAT :name)) newAttributeTypes)))))]
      (doall (map (fn
                     [_name]
                     (addXTAttribute className _name)) attributeTypesVec))
      (doall (map (fn
                     [pUnique]
                     (makePropertyListUnique (makeUniqIndexName className pUnique) className pUnique)) pUniques)))
    (catch Exception E (do
                         (.printStackTrace E)
                         (.getMessage E)))))

(defn createXTClasses
  "Create Abstract NodeTypeAbs and RelationTypeAbs classes"
  []
  (try 
    (doall (pcalls (fn []
                     (createClass "NodeTypeAbs" "V" true))
                   (fn []
                     (createClass "RelationTypeAbs" "E" true))))
    (addXTAttributes "NodeTypeAbs" [] [{:name "name" :datatype "STRING" :subjectTypes nil :constraintsVec [{:cname "MANDATORY" :cval "TRUE"} {:cname "NOTNULL" :cval "TRUE"} {:cname "MIN" :cval "1"}]} {:name "altname" :datatype "STRING" :subjectTypes nil :constraintsVec [{:cname "MANDATORY" :cval "TRUE"} {:cname "NOTNULL" :cval "TRUE"} {:cname "MIN" :cval "1"}]}] [["name"]])
    (addXTAttributes "RelationTypeAbs" ["name" "altname"] [] [["name"]])
    (catch Exception E (do
                         (.printStackTrace E)
                         (.getMessage E)))))

(defn gnowsysInit
  "Creates all essential Classes, All ValueConstraints"
  []
  (doall (pcalls createATClass createVCClass createCATClass))
  (println "Added Base classes")
  (createAllVC)
  (createXTClasses))


;; TODO FOUND Cause of abnormal errors. variables defined using 'def' exist outside 'scope' .... noob mistake. Henceforth... using let instead of def

