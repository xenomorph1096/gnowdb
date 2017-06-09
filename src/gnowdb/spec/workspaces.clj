(ns gnowdb.spec.workspaces
  (:gen-class)
  (:require [gnowdb.neo4j.gneo :as gneo])
)

(defn- prepareNodeClass
  "Applies relationships to GDB_Node class and adds all dependencies required for Workspaces"
	[]
  (gneo/createClass :className "GDB_MemberOfWorkspace" :classType "RELATION" :isAbstract? false :properties {})
  (gneo/addRelApplicableType :className "GDB_MemberOfWorkspace" :applicationType "Source" :applicableClassName "GDB_Node")
  (gneo/createClass :className "GDB_CreatedBy" :classType "RELATION" :isAbstract? false :properties {})
  (gneo/addRelApplicableType :className "GDB_CreatedBy" :applicationType "Source" :applicableClassName "GDB_Node")
  (gneo/createClass :className "GDB_LastModifiedBy" :classType "RELATION" :isAbstract? false :properties {})
  (gneo/addRelApplicableType :className "GDB_LastModifiedBy" :applicationType "Source" :applicableClassName "GDB_Node")
)

(defn- createAbstractWorkspaceClass
  "Creates the Parent GDB_Workspace class"
  []
  (gneo/createClass :className "GDB_Workspace" :classType "NODE" :isAbstract? true :properties {} :subClassOf ["GDB_Node"])
  (gneo/createAttributeType :_name "GDB_GroupType" :_datatype "java.lang.String")
  (gneo/createAttributeType :_name "GDB_EditingPolicy" :_datatype "java.lang.String")
  (gneo/addClassAT :_atname "GDB_GroupType" :className "GDB_Workspace")
  (gneo/addClassAT :_atname "GDB_EditingPolicy" :className "GDB_Workspace")
  (gneo/addATVR :_atname "GDB_GroupType" :fnName "GDB_Enum" :constraintValue ["Public", "Private", "Anonymous"])
  (gneo/addATVR :_atname "GDB_EditingPolicy" :fnName "GDB_Enum" :constraintValue ["Editable_Moderated", "Editable_Non-Moderated", "Non-Editable"])
  (gneo/addRelApplicableType :className "GDB_MemberOfWorkspace" :applicationType "Target" :applicableClassName "GDB_Workspace")
  (gneo/addClassNC :className "GDB_Workspace" :constraintType "EXISTANCE" :constraintTarget "NODE" :constraintValue "GDB_GroupType")
  (gneo/addClassNC :className "GDB_Workspace" :constraintType "EXISTANCE" :constraintTarget "NODE" :constraintValue "GDB_EditingPolicy")
)

(defn- createPersonalWorkspaceClass
  "Creates the GDB_PersonalWorkspace class, whose instances will be personal workspaces"
  []
  (gneo/createClass :className "GDB_PersonalWorkspace" :classType "NODE" :isAbstract? false :properties {} :subClassOf ["GDB_Workspace"])
  (gneo/addRelApplicableType :className "GDB_CreatedBy" :applicationType "Target" :applicableClassName "GDB_PersonalWorkspace")
  (gneo/addRelApplicableType :className "GDB_LastModifiedBy" :applicationType "Target" :applicableClassName "GDB_PersonalWorkspace")
)

(defn- createGroupWorkspaceClass
  "Creates the GDB_GroupWorkspace class, whose instances will be group workspaces"
  []
  (gneo/createClass :className "GDB_MemberOfGroup" :classType "RELATION" :isAbstract? false :properties {})
  (gneo/addRelApplicableType :className "GDB_MemberOfGroup" :applicationType "Source" :applicableClassName "GDB_PersonalWorkspace")
  (gneo/createClass :className "GDB_AdminOfGroup" :classType "RELATION" :isAbstract? false :properties {})
  (gneo/addRelApplicableType :className "GDB_AdminOfGroup" :applicationType "Source" :applicableClassName "GDB_PersonalWorkspace")
  (gneo/createClass :className "GDB_GroupWorkspace" :classType "NODE" :isAbstract? false :properties {} :subClassOf ["GDB_Workspace"])
  (gneo/addRelApplicableType :className "GDB_MemberOfGroup" :applicationType "Target" :applicableClassName "GDB_GroupWorkspace")
  (gneo/addRelApplicableType :className "GDB_AdminOfGroup" :applicationType "Target" :applicableClassName "GDB_GroupWorkspace")
)

(defn instantiateGroupWorkspace
  "Creates Group Workspaces
    :groupType can be Public, Private or Anonymous
    :editingPolicy can be Non-Editable, Editable_Moderated, Editable_Non-Moderated
    :displayName should be the displayName of the group
    :createdBy should be the name of the user who created the workspace
  "
	[& {:keys [:groupType :editingPolicy :displayName :alternateName :description :createdBy :relationshipsOnly?] :or {:groupType "Public" :editingPolicy "Non-Editable" :alternateName "[]" :createdBy "ADMIN" :description "" :relationshipsOnly? false}}]
	(if (false? relationshipsOnly?)
		(gneo/createNodeClassInstances :className "GDB_GroupWorkspace" :nodeList 		[{
																							"GDB_DisplayName" displayName
																							"GDB_GroupType" groupType
																							"GDB_EditingPolicy" editingPolicy
																							"GDB_AlternateName" alternateName
																							"GDB_ModifiedAt" (.toString (new java.util.Date))
																							"GDB_CreatedAt" (.toString (new java.util.Date))
																							"GDB_Description" description
																						}]
		)
	)
	(gneo/createRelationClassInstances :className "GDB_CreatedBy" :relList 	[{
																						:fromClassName "GDB_GroupWorkspace"
																						:fromPropertyMap {"GDB_DisplayName" displayName}
																						:toClassName "GDB_PersonalWorkspace"
																						:toPropertyMap {"GDB_DisplayName" createdBy}
																						:propertyMap {}
																					}]
	)
	(gneo/createRelationClassInstances :className "GDB_LastModifiedBy" :relList 	[{
																						:fromClassName "GDB_GroupWorkspace"
																						:fromPropertyMap {"GDB_DisplayName" displayName}
																						:toClassName "GDB_PersonalWorkspace"
																						:toPropertyMap {"GDB_DisplayName" createdBy}
																						:propertyMap {}
																					}]
	)
	(gneo/createRelationClassInstances :className "GDB_MemberOfWorkspace" :relList 	[{
																						:fromClassName "GDB_GroupWorkspace"
																						:fromPropertyMap {"GDB_DisplayName" displayName}
																						:toClassName "GDB_GroupWorkspace"
																						:toPropertyMap {"GDB_DisplayName" displayName}
																						:propertyMap {}
																					}]
	)
	(gneo/createRelationClassInstances :className "GDB_MemberOfGroup" :relList 		[{
																						:fromClassName "GDB_PersonalWorkspace"
																						:fromPropertyMap {"GDB_DisplayName" createdBy}
																						:toClassName "GDB_GroupWorkspace"
																						:toPropertyMap {"GDB_DisplayName" displayName}
																						:propertyMap {}
																					}]
	)
	(gneo/createRelationClassInstances :className "GDB_AdminOfGroup" :relList 		[{
																						:fromClassName "GDB_PersonalWorkspace"
																						:fromPropertyMap {"GDB_DisplayName" createdBy}
																						:toClassName "GDB_GroupWorkspace"
																						:toPropertyMap {"GDB_DisplayName" displayName}
																						:propertyMap {}
																					}]
	)
)

(defn instantiatePersonalWorkspace
  "Creates Group Workspaces
    :displayName should be the name of the user
    :createdBy should be the name of the user who created this one, default is ADMIN
    :memberOfGroup should be the name of the Group the user is a member of, default is HOME
  "
	[& {:keys [:displayName :alternateName :createdBy :memberOfGroup :description :relationshipsOnly?] :or {:alternateName "[]" :createdBy "ADMIN" :memberOfGroup "HOME" :description "" :relationshipsOnly? false}}]
	(if (false? relationshipsOnly?)
		(gneo/createNodeClassInstances :className "GDB_PersonalWorkspace" :nodeList 	[{
																							"GDB_DisplayName" displayName
																							"GDB_GroupType" "Public"
																							"GDB_EditingPolicy" "Editable_Moderated"
																							"GDB_AlternateName" alternateName
																							"GDB_ModifiedAt" (.toString (new java.util.Date))
																							"GDB_CreatedAt" (.toString (new java.util.Date))
																							"GDB_Description" description
																						}]
		)
	)
	(gneo/createRelationClassInstances :className "GDB_CreatedBy" :relList 	[{
																						:fromClassName "GDB_PersonalWorkspace"
																						:fromPropertyMap {"GDB_DisplayName" displayName}
																						:toClassName "GDB_PersonalWorkspace"
																						:toPropertyMap {"GDB_DisplayName" createdBy}
																						:propertyMap {}
																					}]
	)
	(gneo/createRelationClassInstances :className "GDB_LastModifiedBy" :relList 	[{
																						:fromClassName "GDB_PersonalWorkspace"
																						:fromPropertyMap {"GDB_DisplayName" displayName}
																						:toClassName "GDB_GroupWorkspace"
																						:toPropertyMap {"GDB_DisplayName" createdBy}
																						:propertyMap {}
																					}]
	)
	(gneo/createRelationClassInstances :className "GDB_MemberOfWorkspace" :relList 	[{
																						:fromClassName "GDB_PersonalWorkspace"
																						:fromPropertyMap {"GDB_DisplayName" displayName}
																						:toClassName "GDB_PersonalWorkspace"
																						:toPropertyMap {"GDB_DisplayName" displayName}
																						:propertyMap {}
																					}]
	)
	(gneo/createRelationClassInstances :className "GDB_MemberOfGroup" :relList 		[{
																						:fromClassName "GDB_PersonalWorkspace"
																						:fromPropertyMap {"GDB_DisplayName" displayName}
																						:toClassName "GDB_GroupWorkspace"
																						:toPropertyMap {"GDB_DisplayName" memberOfGroup}
																						:propertyMap {}
																					}]
	)
)

(defn- instantiateDefaultWorkspaces
  "Instantiates ADMIN user and HOME workspace and adds them as dependents on each other"
  []
  (gneo/createNodeClassInstances :className "GDB_GroupWorkspace" :nodeList    [{
                                            "GDB_DisplayName" "HOME"
                                            "GDB_GroupType" "Public"
                                            "GDB_EditingPolicy" "Non-Editable"
                                            "GDB_AlternateName" "[]"
                                            "GDB_ModifiedAt" (.toString (new java.util.Date))
                                            "GDB_CreatedAt" (.toString (new java.util.Date))
                                            "GDB_Description" ""
                                          }]
  )
  (gneo/createNodeClassInstances :className "GDB_PersonalWorkspace" :nodeList   [{
                                            "GDB_DisplayName" "ADMIN"
                                            "GDB_GroupType" "Public"
                                            "GDB_EditingPolicy" "Editable_Moderated"
                                            "GDB_AlternateName" "[]"
                                            "GDB_ModifiedAt" (.toString (new java.util.Date))
                                            "GDB_CreatedAt" (.toString (new java.util.Date))
                                            "GDB_Description" ""
                                          }]
  )
  (instantiateGroupWorkspace :displayName "HOME" :relationshipsOnly? true)
  (instantiatePersonalWorkspace :displayName "ADMIN" :relationshipsOnly? true)
)

(defn- editLastModified
  [& {:keys [:editor :groupName]}]
  (gneo/deleteRelations
              :fromNodeLabel ["GDB_GroupWorkspace"]
              :fromNodeProperties {"GDB_DisplayName" groupName}
              :relationshipType "GDB_LastModifiedBy"
            )
  (gneo/createRelationClassInstances :className "GDB_LastModifiedBy" :relList    [{
                                    :fromClassName "GDB_GroupWorkspace"
                                    :fromPropertyMap {"GDB_DisplayName" groupName}
                                    :toClassName "GDB_PersonalWorkspace"
                                    :toPropertyMap {"GDB_DisplayName" editor}
                                    :propertyMap {}
                                  }]
  )
  (gneo/editNodeProperties :label "GDB_GroupWorkspace" :parameters {"GDB_DisplayName" groupName} :changeMap {"GDB_ModifiedAt" (.toString (new java.util.Date))})
)

(defn addMemberToGroupWorkspace
  "Adds member to group workspace"
  [& {:keys [:newMemberName :groupName :adminName]}]
    (let [workspace (first (gneo/getNodes 
                        :label "GDB_GroupWorkspace" 
                        :parameters  {
                                      "GDB_DisplayName" groupName
                                      }
                    ))
          admins (map #(((% :start) :properties) "GDB_DisplayName")
                    (gneo/getRelations :toNodeLabel ["GDB_GroupWorkspace"] 
                                      :toNodeParameters {"GDB_DisplayName" groupName}
                                      :relationshipType "GDB_AdminOfGroup"
                                      :nodeInfo? true
                    )
                  )
        ]
      (if (or (= ((workspace :properties) "GDB_GroupType") "Public") (and (= ((workspace :properties) "GDB_GroupType") "Private") (some #{adminName} admins)))
        (do
          (gneo/createRelationClassInstances :className "GDB_MemberOfGroup" :relList    [{
                                            :fromClassName "GDB_PersonalWorkspace"
                                            :fromPropertyMap {"GDB_DisplayName" newMemberName}
                                            :toClassName "GDB_GroupWorkspace"
                                            :toPropertyMap {"GDB_DisplayName" groupName}
                                            :propertyMap {}
                                          }]
          )
          (editLastModified :groupName groupName :editor adminName)
        )
        {:results [] :summary {:summaryMap {} :summaryString "Membership could not be created, either the group is Anonymous or the user does not have admin permissions"}}
      )
    )
)

(defn addAdminToGroup
  [& {:keys [:newAdminName :groupName :adminName]}]
  (let [
          admins (map #(((% :start) :properties) "GDB_DisplayName")
                    (gneo/getRelations :toNodeLabel ["GDB_GroupWorkspace"] 
                                      :toNodeParameters {"GDB_DisplayName" groupName}
                                      :relationshipType "GDB_AdminOfGroup"
                                      :nodeInfo? true
                    )
                  )
        ]
        (if (some #{adminName} admins)
          (do
            (gneo/createRelationClassInstances :className "GDB_AdminOfGroup" :relList    [{
                                              :fromClassName "GDB_PersonalWorkspace"
                                              :fromPropertyMap {"GDB_DisplayName" newAdminName}
                                              :toClassName "GDB_GroupWorkspace"
                                              :toPropertyMap {"GDB_DisplayName" groupName}
                                              :propertyMap {}
                                            }]
            )
            (editLastModified :groupName groupName :editor adminName)
          )
          {:results [] :summary {:summaryMap {} :summaryString "The user could not be granted Administrator permissions as it is not authorized by valid admin."}}
        )

  )
) 

(defn init
  []
  (prepareNodeClass)
  (createAbstractWorkspaceClass)
  (createPersonalWorkspaceClass)
  (createGroupWorkspaceClass)
  (instantiateDefaultWorkspaces)
)
