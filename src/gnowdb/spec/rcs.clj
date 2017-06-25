(ns gnowdb.spec.rcs
  (:gen-class)
  (:require [gnowdb.neo4j.gneo :as gneo]
  )
)

(defn updateLastModified
  [& {:keys [:editor :resourceIDMap :resourceClass]}]
  (gneo/deleteRelation
              :fromNodeLabels [resourceClass]
              :fromNodeProperties resourceIDMap
              :relationshipType "GDB_LastModifiedBy"
  )
  (gneo/createRelationClassInstances :className "GDB_LastModifiedBy" :relList    [{
                                    :fromClassName resourceClass
                                    :fromPropertyMap resourceIDMap
                                    :toClassName "GDB_PersonalWorkspace"
                                    :toPropertyMap {"GDB_DisplayName" editor}
                                    :propertyMap {}
                                  }]
  )
  (gneo/editNodeProperties :label resourceClass :parameters resourceIDMap :changeMap {"GDB_ModifiedAt" (.toString (new java.util.Date))})
  ;Call to RCS Goes here with resourceIDMap and resourceClass
  nil
)

(defn instantiateResourceRCS
  [& {:keys [:resourceIDMap :resourceClass]}]
  ;instantiate RCS here. Check if the resource passes is an instance of an already defined class!!
)
