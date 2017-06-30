(ns gnowdb.spec.init
  (:gen-class)
  (:require [gnowdb.neo4j.gneo :as gneo]
            [gnowdb.spec.workspaces :as workspaces]
            [gnowdb.spec.files :as files]
            [progrock.core :as pr]
            )
  )

(def progressBar (pr/progress-bar 100))

(defn tickProgressBar
  [amountToAdvance]
  (def progressBar (pr/tick progressBar amountToAdvance))
  (if (= (:progress progressBar) (:total progressBar))
    (do
      (pr/print (pr/done progressBar))
      (def progressBar (pr/progress-bar 100))
      nil
      )
    (pr/print progressBar)
    )
  )

(defn- createAbstractNodeClass
  "Creates GDB_Node class, it is the only class that has no superclasses"
  []
  (gneo/createClass :className "GDB_Node" :classType "NODE" :isAbstract? true :subClassOf [] :properties {})
  (gneo/createAttributeType :_name "GDB_DisplayName" :_datatype "java.lang.String")
  (gneo/createAttributeType :_name "GDB_AlternateName" :_datatype "java.lang.String")
  (gneo/createAttributeType :_name "GDB_CreatedAt" :_datatype "java.lang.String")
  (gneo/createAttributeType :_name "GDB_ModifiedAt" :_datatype "java.lang.String")
  (gneo/createAttributeType :_name "GDB_Description" :_datatype "java.lang.String")
  (gneo/createAttributeType :_name "GDB_UHRID" :_datatype "java.lang.String")
  (gneo/addClassAT :_atname "GDB_DisplayName" :className "GDB_Node")
  (gneo/addClassAT :_atname "GDB_AlternateName" :className "GDB_Node")
  (gneo/addClassAT :_atname "GDB_CreatedAt" :className "GDB_Node")
  (gneo/addClassAT :_atname "GDB_ModifiedAt" :className "GDB_Node")
  (gneo/addClassAT :_atname "GDB_Description" :className "GDB_Node")
                                        ;(gneo/addClassAT :_atname "GDB_UHRID" :className "GDB_Node")
  (gneo/addClassNC :constraintType "EXISTANCE" :constraintTarget "NODE" :constraintValue "GDB_DisplayName" :className "GDB_Node") 
  (gneo/addClassNC :constraintType "EXISTANCE" :constraintTarget "NODE" :constraintValue "GDB_CreatedAt" :className "GDB_Node") 
  (gneo/addClassNC :constraintType "EXISTANCE" :constraintTarget "NODE" :constraintValue "GDB_ModifiedAt" :className "GDB_Node")
  (gneo/addClassNC :constraintType "NODEKEY" :constraintTarget "NODE" :constraintValue ["GDB_DisplayName"] :className "GDB_Node")
                                        ;(gneo/addClassNC :constraintType "NODEKEY" :constraintTarget "NODE" :constraintValue ["GDB_UHRID"] :className "GDB_Node")
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
  (pr/print progressBar)
  (gneo/defineInitialConstraints)
  (tickProgressBar 10)
  (createAbstractNodeClass)
  (tickProgressBar 10)
  (addCustomFunctionality)
  (tickProgressBar 10)
  (workspaces/init) 
  (tickProgressBar 40)
  (files/init)
  (tickProgressBar 30)
  )
