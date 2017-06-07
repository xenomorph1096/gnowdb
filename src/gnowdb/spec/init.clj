(ns gnowdb.spec.init
  (:gen-class)
  (:require [gnowdb.neo4j.gneo :as gneo]
            [gnowdb.spec.workspaces :as workspaces]
  )
)

(defn- createAbstractNodeClass
  []
  (gneo/createClass :className "GDB_Node" :classType "NODE" :isAbstract? true :subClassOf [] :properties {})
  (gneo/createAttributeType :_name "GDB_UUID" :_datatype "java.lang.String")
  (gneo/createAttributeType :_name "GDB_Display_Name" :_datatype "java.lang.String")
  (gneo/createAttributeType :_name "GDB_Alternate_Name" :_datatype "java.lang.String")
  (gneo/createAttributeType :_name "GDB_Created_At" :_datatype "java.lang.String")
  (gneo/createAttributeType :_name "GDB_Modified_At" :_datatype "java.lang.String")
  (gneo/createAttributeType :_name "GDB_Description" :_datatype "java.lang.String")
  (gneo/addClassAT :_atname "GDB_UUID" :className "GDB_Node")
  (gneo/addClassAT :_atname "GDB_Display_Name" :className "GDB_Node")
  (gneo/addClassAT :_atname "GDB_Alternate_Name" :className "GDB_Node")
  (gneo/addClassAT :_atname "GDB_Created_At" :className "GDB_Node")
  (gneo/addClassAT :_atname "GDB_Modified_At" :className "GDB_Node")
  (gneo/addClassAT :_atname "GDB_Description" :className "GDB_Node")
)

(defn- addCustomFunctionality
  []
  ;Enum Functionality, just need to pass the list of desired values as :constraintValue
  (gneo/createCustomFunction  :fnName "GDB_Enum" 
                              :fnString (pr-str 
                                          '(fn 
                                            [[enumVal], enumList]
                                            (not (nil? (some #{enumVal} (into [] enumList))))
                                          )
                                        )
  )
)

(defn init
  []
  (gneo/defineInitialConstraints)
  (createAbstractNodeClass)
  (addCustomFunctionality)
  (workspaces/init)
)