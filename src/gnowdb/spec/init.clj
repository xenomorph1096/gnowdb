(ns gnowdb.spec.init
  (:gen-class)
  (:require [gnowdb.neo4j.gneo :as gneo]
            [gnowdb.spec.workspaces :as workspaces]
  )
)

(defn- createAbstractNodeClass
  []
  (gneo/createClass :className "GDB_Node" :classType "NODE" :isAbstract? true :subClassOf [] :properties {})
  (gneo/createAttributeType :_name "GDB_DisplayName" :_datatype "java.lang.String")
  (gneo/createAttributeType :_name "GDB_AlternateName" :_datatype "java.lang.String")
  (gneo/createAttributeType :_name "GDB_CreatedAt" :_datatype "java.lang.String")
  (gneo/createAttributeType :_name "GDB_ModifiedAt" :_datatype "java.lang.String")
  (gneo/createAttributeType :_name "GDB_Description" :_datatype "java.lang.String")
  (gneo/addClassAT :_atname "GDB_DisplayName" :className "GDB_Node")
  (gneo/addClassAT :_atname "GDB_AlternateName" :className "GDB_Node")
  (gneo/addClassAT :_atname "GDB_CreatedAt" :className "GDB_Node")
  (gneo/addClassAT :_atname "GDB_ModifiedAt" :className "GDB_Node")
  (gneo/addClassAT :_atname "GDB_Description" :className "GDB_Node")
  (gneo/addClassNC :constraintType "EXISTANCE" :constraintTarget "NODE" :constraintValue "GDB_DisplayName" :className "GDB_Node") 
  (gneo/addClassNC :constraintType "EXISTANCE" :constraintTarget "NODE" :constraintValue "GDB_CreatedAt" :className "GDB_Node") 
  (gneo/addClassNC :constraintType "EXISTANCE" :constraintTarget "NODE" :constraintValue "GDB_ModifiedAt" :className "GDB_Node")
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