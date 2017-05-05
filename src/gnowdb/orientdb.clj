(ns gnowdb.orientdb)
(import [com.tinkerpop.blueprints.impls.orient OrientGraphFactory OrientGraph OrientVertex OrientEdge OrientVertexType OrientEdgeType]
        [com.orientechnologies.orient.core.sql OCommandSQL]
        [com.orientechnologies.orient.core.command.script OCommandScript])

(defn getOrientDBDetails
  "Get OrientDB Connection info"
  []
  ["remote:localhost/mydb" "admin" "admin"])

(defn connect
  "Connect to orientdb and get OrientGraphFactory Object"
  []
  (def orientDBDetails (getOrientDBDetails))
  (def graphFactory (OrientGraphFactory. (orientDBDetails 0) (orientDBDetails 1) (orientDBDetails 2)))
  graphFactory)

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

(defn getVertices
  "Get All Vertices from OrientGraphFactory object.
  Returns an Iterable"
  [^OrientGraphFactory orientDBGraphFactoryObject]
  (try
    (def orientGraph (.getNoTx orientDBGraphFactoryObject))
    (def vertices (.getVertices orientGraph))
    (catch Exception E (.toString E))
    (finally (.shutdown orientGraph)))
  (def orientGraph (.getNoTx orientDBGraphFactoryObject))
  (def vertices (.getVertices orientGraph))
  vertices)

(defn createDCProperties
  "Create Dublin Core Metadata Properties for Class"
  [^OrientGraph orientGraph
   ^String className]
  (def DCProperties [{:name "Title" :type "STRING"} {:name "Creator" :type "STRING"} {:name "Subject" :type "STRING"} {:name "Description" :type "STRING"} {:name "Publisher" :type "STRING"} {:name "Contributor" :type "STRING"} {:name "Date" :type "DATE"} {:name "Type" :type "STRING"} {:name "Format" :type "STRING"} {:name "Identifier" :type "STRING"} {:name "Source" :type "STRING"} {:name "Language" :type "STRING"} {:name "Relation" :type "STRING"} {:name "Coverage" :type "STRING"} {:name "Rights" :type "STRING"}])
  (dorun (map (fn [DCP]
                (execCommand orientGraph (str "CREATE PROPERTY " className "." (DCP :name) " IF NOT EXISTS " (DCP :type)))) DCProperties)))

(defn createDCGenClass
  "Create Generic Abstract DC Class with optional Dublin Core Metadata elements"
  []
  (def orientGF (connect))
  (def orientGraph (.getTx orientGF))
  (try
    (execCommand orientGraph "CREATE CLASS DCGen IF NOT EXISTS ABSTRACT")
    (createDCProperties orientGraph "DCGen")
    (catch Exception E
      (do
        (.printStackTrace E)
        (.getMessage E)))
    (finally (.shutdown orientGraph))))


(defn createClass
  "Create a Class"
  [^String className
   ^String superClass
   ^Boolean isAbstract]
  (def orientGF (connect))
  (def orientGraph (.getTx orientGF))
  (try
    (execCommand orientGraph (str "CREATE CLASS " className " IF NOT EXISTS EXTENDS " superClass (if isAbstract " ABSTRACT" "")))
    (catch Exception E
      (do
        (.printStackTrace E)
        (.getMessage E)))
    (finally (.shutdown orientGraph))))

(defn createDCVertexClass
  "Create the DCVertex Class"
  []
  (createClass "DCVertex" "V,DCGen" true))

(defn createDCEdgeClass
  "Create the DCEdge Class"
  []
  (createClass "DCEdge" "E,DCGen" true))

