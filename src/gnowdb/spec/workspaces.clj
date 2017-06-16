(ns gnowdb.spec.workspaces
  (:gen-class)
  (:require [gnowdb.neo4j.gneo :as gneo]
  )
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
  (gneo/createClass :className "GDB_PendingReview" :classType "RELATION" :isAbstract? false :properties {})
  (gneo/addRelApplicableType :className "GDB_PendingReview" :applicationType "Source" :applicableClassName "GDB_Node")
  nil
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
  (gneo/addATVR :_atname "GDB_EditingPolicy" :fnName "GDB_Enum" :constraintValue ["Editable_Moderated", "Editable_Non-Moderated", "Editable_Admin-Only", "Archive"])
  (gneo/addRelApplicableType :className "GDB_MemberOfWorkspace" :applicationType "Target" :applicableClassName "GDB_Workspace")
  (gneo/addClassNC :className "GDB_Workspace" :constraintType "EXISTANCE" :constraintTarget "NODE" :constraintValue "GDB_GroupType")
  (gneo/addClassNC :className "GDB_Workspace" :constraintType "EXISTANCE" :constraintTarget "NODE" :constraintValue "GDB_EditingPolicy")
  nil
)

(defn- createPersonalWorkspaceClass
  "Creates the GDB_PersonalWorkspace class, whose instances will be personal workspaces"
  []
  (gneo/createClass :className "GDB_PersonalWorkspace" :classType "NODE" :isAbstract? false :properties {} :subClassOf ["GDB_Workspace"])
  (gneo/addRelApplicableType :className "GDB_CreatedBy" :applicationType "Target" :applicableClassName "GDB_PersonalWorkspace")
  (gneo/addRelApplicableType :className "GDB_LastModifiedBy" :applicationType "Target" :applicableClassName "GDB_PersonalWorkspace")
  nil
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
  (gneo/addRelApplicableType :className "GDB_PendingReview" :applicationType "Target" :applicableClassName "GDB_GroupWorkspace")
  nil
)

(defn- generateUHRID
	[& {:keys [resourceUHRID workspaceName]}]
	(str workspaceName "/" resourceUHRID)
)

(defn- deleteWorkspaceFromUHRID
	[& {:keys [resourceUHRID workspaceName]}]
	(clojure.string/replace resourceUHRID (str workspaceName "/") "")
)

(defn instantiateGroupWorkspace
  "Creates Group Workspaces
    :groupType can be Public, Private or Anonymous
    :editingPolicy can be Editable_Admin-Only, Editable_Moderated, Editable_Non-Moderated or Archive
    :displayName should be the displayName of the group
    :createdBy should be the name of the user who created the workspace
  "
	[& {:keys [:groupType :editingPolicy :displayName :alternateName :description :createdBy :relationshipsOnly?] :or {:groupType "Public" :editingPolicy "Editable_Non-Moderated" :alternateName "[]" :createdBy "admin" :description "" :relationshipsOnly? false}}]
	(if (false? relationshipsOnly?)
		(gneo/createNodeClassInstances :className "GDB_GroupWorkspace" :nodeList 		[{
																							"GDB_DisplayName" displayName
																							"GDB_GroupType" groupType
																							"GDB_EditingPolicy" editingPolicy
																							"GDB_AlternateName" alternateName
																							"GDB_ModifiedAt" (.toString (new java.util.Date))
																							"GDB_CreatedAt" (.toString (new java.util.Date))
																							"GDB_Description" description
																		;					"GDB_UHRID" (generateUHRID :resourceUHRID displayName :workspaceName displayName)
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
  nil
)

(defn instantiatePersonalWorkspace
  "Creates Group Workspaces
    :displayName should be the name of the user
    :createdBy should be the name of the user who created this one, default is admin
    :memberOfGroup should be the name of the Group the user is a member of, default is home
  "
	[& {:keys [:displayName :alternateName :createdBy :memberOfGroup :description :relationshipsOnly?] :or {:alternateName "[]" :createdBy "admin" :memberOfGroup "home" :description "" :relationshipsOnly? false}}]
	(if (false? relationshipsOnly?)
		(gneo/createNodeClassInstances :className "GDB_PersonalWorkspace" :nodeList 	[{
																							"GDB_DisplayName" displayName
																							"GDB_GroupType" "Public"
																							"GDB_EditingPolicy" "Editable_Admin-Only"
																							"GDB_AlternateName" alternateName
																							"GDB_ModifiedAt" (.toString (new java.util.Date))
																							"GDB_CreatedAt" (.toString (new java.util.Date))
																							"GDB_Description" description
																			;				"GDB_UHRID" (generateUHRID :resourceUHRID displayName :workspaceName displayName)
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
  nil
)

(defn- instantiateDefaultWorkspaces
  "Instantiates admin user and home workspace and adds them as dependents on each other"
  []
  (gneo/createNodeClassInstances :className "GDB_GroupWorkspace" :nodeList    [{
                                            "GDB_DisplayName" "home"
                                            "GDB_GroupType" "Public"
                                            "GDB_EditingPolicy" "Editable_Non-Moderated"
                                            "GDB_AlternateName" "[]"
                                            "GDB_ModifiedAt" (.toString (new java.util.Date))
                                            "GDB_CreatedAt" (.toString (new java.util.Date))
                                            "GDB_Description" ""
                                        ;    "GDB_UHRID" (generateUHRID :resourceUHRID "HOME" :workspaceName "HOME")
                                          }]
  )
  (gneo/createNodeClassInstances :className "GDB_PersonalWorkspace" :nodeList   [{
                                            "GDB_DisplayName" "admin"
                                            "GDB_GroupType" "Public"
                                            "GDB_EditingPolicy" "Editable_Admin-Only"
                                            "GDB_AlternateName" "[]"
                                            "GDB_ModifiedAt" (.toString (new java.util.Date))
                                            "GDB_CreatedAt" (.toString (new java.util.Date))
                                            "GDB_Description" ""
                                         ;   "GDB_UHRID" (generateUHRID :resourceUHRID "ADMIN" :workspaceName "ADMIN")
                                          }]
  )
  (instantiateGroupWorkspace :displayName "home" :relationshipsOnly? true)
  (instantiatePersonalWorkspace :displayName "admin" :relationshipsOnly? true)
)

(defn getAdminList
  [groupName]
  (map #(((% :start) :properties) "GDB_DisplayName")
      (gneo/getRelations :toNodeLabel ["GDB_GroupWorkspace"] 
                        :toNodeParameters {"GDB_DisplayName" groupName}
                        :relationshipType "GDB_AdminOfGroup"
                        :nodeInfo? true
      )
  )
)

(defn getMemberList
  [groupName]
  (map #(((% :start) :properties) "GDB_DisplayName")
      (gneo/getRelations :toNodeLabel ["GDB_GroupWorkspace"] 
                        :toNodeParameters {"GDB_DisplayName" groupName}
                        :relationshipType "GDB_MemberOfGroup"
                        :nodeInfo? true
      )
  )
)

(defn getPublishedResources
  [groupName]
  (map #(((% :start) :properties) "GDB_DisplayName")
      (gneo/getRelations :toNodeLabel ["GDB_GroupWorkspace"] 
                        :toNodeParameters {"GDB_DisplayName" groupName}
                        :relationshipType "GDB_MemberOfWorkspace"
                        :nodeInfo? true
      )
  )
)

(defn getPendingResources
  [groupName]
  (map #((% :start) :properties)
      (gneo/getRelations :toNodeLabel ["GDB_GroupWorkspace"] 
                        :toNodeParameters {"GDB_DisplayName" groupName}
                        :relationshipType "GDB_PendingReview"
                        :nodeInfo? true
      )
  )
)

(defn getGroupType
  [groupName]
    (((first (gneo/getNodes 
                        :label "GDB_GroupWorkspace" 
                        :parameters  {
                                      "GDB_DisplayName" groupName
                                      }
    )) :properties) "GDB_GroupType")
)

(defn getEditingPolicy
  [groupName]
    (((first (gneo/getNodes 
                        :label "GDB_GroupWorkspace" 
                        :parameters  {
                                      "GDB_DisplayName" groupName
                                      }
    )) :properties) "GDB_EditingPolicy")
)

(defn setGroupType
  [& {:keys [groupName adminName groupType]}]
  (if (.contains (getAdminList groupName) adminName)
    (gneo/editNodeProperties :label "GDB_GroupWorkspace" :parameters {"GDB_DisplayName" groupName} :changeMap {"GDB_GroupType" groupType})
  )
  nil
)

(defn setEditingPolicy
  [& {:keys [groupName adminName editingPolicy]}]
  (if (.contains (getAdminList groupName) adminName)
    (gneo/editNodeProperties :label "GDB_GroupWorkspace" :parameters {"GDB_DisplayName" groupName} :changeMap {"GDB_GroupType" editingPolicy})
  )
  nil
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
  nil
)

(defn- getTypeOfWorkspaces
  "Determines types of workspaces the resource is published to"
  [& {:keys [:resourceIDMap :resourceClass]}]
  (into #{} 
    (map #(((% :end) :properties) "GDB_GroupType")
      (gneo/getRelations  :fromNodeLabel [resourceClass]
                          :fromNodeParameters resourceIDMap
                          :relationshipType "GDB_MemberOfWorkspace"
                          :nodeInfo? true
      )
    )
  )
)


(defn- publishToUnmoderatedGroup
  "Publish nodes to unmoderated groups i.e. without admin check"
  [& {:keys [:username :groupName :resourceIDMap :resourceClass]}]
  (gneo/createRelationClassInstances :className "GDB_MemberOfWorkspace" :relList  [{
                                                      :fromClassName resourceClass
                                                      :fromPropertyMap resourceIDMap
                                                      :toClassName "GDB_GroupWorkspace"
                                                      :toPropertyMap {"GDB_DisplayName" groupName}
                                                      :propertyMap {}
                                                    }]
  )

  ; (let [resourceUHRID
	 ;  	(((first (gneo/getNodes :label resourceClass :parameters resourceIDMap)) :properties) "GDB_UHRID")]
	 ;  (gneo/editNodeProperties 	:label resourceClass 
	 ;  							:parameters resourceIDMap 
	 ;  							:changeMap {"GDB_UHRID" (generateUHRID 	:resourceUHRID resourceUHRID 
	 ;  																	:workspaceName groupName)})
  ; )
  (editLastModified :editor username :groupName groupName)
)

(defn- publishToModeratedGroup
  "Publish nodes to moderated groups i.e. with admin check"
  [& {:keys [:username :groupName :resourceIDMap :resourceClass]}]
  (if (.contains (getAdminList groupName) username)
    (publishToUnmoderatedGroup :username username :groupName groupName :resourceIDMap resourceIDMap :resourceClass resourceClass)
    (gneo/createRelationClassInstances  :className "GDB_PendingReview"  
                                        :relList  [{
                                                :fromClassName resourceClass
                                                :fromPropertyMap resourceIDMap
                                                :toClassName "GDB_GroupWorkspace"
                                                :toPropertyMap {"GDB_DisplayName" groupName}
                                                :propertyMap {}
                                              }]
    )
  )
  (editLastModified :editor username :groupName groupName) 
)

(defn- publishToAdminOnlyGroup
  [& {:keys [:username :groupName :resourceIDMap :resourceClass]}]
  (if (.contains (getAdminList groupName) username)
    (do
      (publishToUnmoderatedGroup :username username :groupName groupName :resourceIDMap resourceIDMap :resourceClass resourceClass)
      (editLastModified :editor username :groupName groupName)
    )
  )
)

(defn crossPublishAllowed?
  "Determines whether cross-publication is allowed for a particular resource"
  [& {:keys [:resourceIDMap :resourceClass :groupType]}]
  (let [workspaceTypes (getTypeOfWorkspaces resourceIDMap resourceClass)]
    (if (and (= workspaceTypes #{"Private"}) (not= groupType "Private"))
      false
      true
    )
  )
)

(defn addMemberToGroup
  "Adds member to group workspace"
  [& {:keys [:newMemberName :groupName :adminName]}]
    (let [workspaceType (getGroupType groupName)
          admins (getAdminList groupName)
        ]
      (if (or (= workspaceType "Public") (and (= workspaceType "Private") (.contains admins adminName)))
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
    nil
)

(defn addAdminToGroup
  "Adds an Administrator to a group workspace"
  [& {:keys [:newAdminName :groupName :adminName]}]
  (let [
          admins (getAdminList groupName)
          members (getMemberList groupName)
        ]
        (if (and (some #{adminName} admins) (some #{newAdminName} members))
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

(defn publishToGroup
  "Publish nodes to Group Workspace"
  [& {:keys [:username :groupName :resourceIDMap :resourceClass]}]
  (let [groupType (getGroupType groupName)
        editingPolicy (getEditingPolicy groupName)
    ]
    (if (and (not= editingPolicy "Archive") (crossPublishAllowed? :resourceIDMap resourceIDMap :resourceClass resourceClass :groupType groupType))
      (if (.contains (getMemberList groupName) username)
        (cond 
          (= editingPolicy "Editable_Moderated") (publishToModeratedGroup :username username :groupName groupName :resourceIDMap resourceIDMap :resourceClass resourceClass)
          (= editingPolicy "Editable_Non-Moderated") (publishToUnmoderatedGroup :username username :groupName groupName :resourceIDMap resourceIDMap :resourceClass resourceClass)
          (= editingPolicy "Editable_Admin-Only") (publishToAdminOnlyGroup :username username :groupName groupName :resourceIDMap resourceIDMap :resourceClass resourceClass)
        )
        (if (= groupType "Anonymous")
          (cond 
            (= editingPolicy "Editable_Moderated") (publishToModeratedGroup :username username :groupName groupName :resourceIDMap resourceIDMap :resourceClass resourceClass)
            (= editingPolicy "Editable_Non-Moderated") (publishToUnmoderatedGroup :username username :groupName groupName :resourceIDMap resourceIDMap :resourceClass resourceClass)
            (= editingPolicy "Editable_Admin-Only") (publishToAdminOnlyGroup :username username :groupName groupName :resourceIDMap resourceIDMap :resourceClass resourceClass)
          )
          "Publish Unsuccessful: User is not a member of the group"
        )
      )
      "Publish Unsuccessful: The group is either non-editable or you're trying to cross-publish in a private group"
    )
  )
)

(defn publishToPersonalWorkspace
  "Publish nodes to personal workspace"
  [& {:keys [:username :resourceIDMap :resourceClass]}]
  (gneo/createRelationClassInstances :className "GDB_MemberOfWorkspace" :relList  [{
                                              :fromClassName resourceClass
                                              :fromPropertyMap resourceIDMap
                                              :toClassName "GDB_PersonalWorkspace"
                                              :toPropertyMap {"GDB_DisplayName" username}
                                              :propertyMap {}
                                          }]
  )
  ; (let [resourceUHRID
	 ;  	(((first (gneo/getNodes :label resourceClass :parameters resourceIDMap)) :properties) "GDB_UHRID")]
	 ;  (gneo/editNodeProperties 	:label resourceClass 
	 ;  							:parameters resourceIDMap 
	 ;  							:changeMap {"GDB_UHRID" (generateUHRID 	:resourceUHRID resourceUHRID 
	 ;  																	:workspaceName username)})
  ; )
)

(defn publishPendingResource
  "Publishes Pending Resources to Group"
  [& {:keys [:adminName :groupName :resourceIDMap :resourceClass]}]
  (gneo/deleteRelations
              :toNodeLabel ["GDB_GroupWorkspace"]
              :toNodeProperties {"GDB_DisplayName" groupName}
              :relationshipType "GDB_PendingReview"
              :fromNodeLabel resourceClass
              :fromNodeProperties resourceIDMap
  )
  (publishToGroup :username adminName :groupName groupName :resourceIDMap resourceIDMap :resourceClass resourceClass)
)

(defn deleteFromUnmoderatedGroup
	[& {:keys [:username :groupName :resourceIDMap :resourceClass]}]
	(gneo/deleteRelations 	:fromNodeLabel [resourceClass]
							:fromNodeParameters resourceIDMap 
							:relationshipType "GDB_MemberOfWorkspace" 
							:toNodeLabel ["GDB_GroupWorkspace"] 
							:toNodeParameters {"GDB_DisplayName" groupName})
	; (let [resourceUHRID
	;   	(((first (gneo/getNodes :label resourceClass :parameters resourceIDMap)) :properties) "GDB_UHRID")]
	;   	(gneo/editNodeProperties 	:label resourceClass 
	; 	  							:parameters resourceIDMap 
	; 	  							:changeMap {"GDB_UHRID" (deleteWorkspaceFromUHRID :resourceUHRID resourceUHRID :workspaceName groupName)})
 ;  	)
	(editLastModified :editor username :groupName groupName) 
)

(defn- deleteFromModeratedGroup
  "Delete nodes from moderated groups i.e. with admin check"
  [& {:keys [:username :groupName :resourceIDMap :resourceClass]}]
  (if (.contains (getAdminList groupName) username)
    (deleteFromUnmoderatedGroup :username username :groupName groupName :resourceIDMap resourceIDMap :resourceClass resourceClass)
	(editLastModified :editor username :groupName groupName) 
  )
)

(defn deleteFromGroup
  "Delete nodes from Group Workspace"
  [& {:keys [:username :groupName :resourceIDMap :resourceClass]}]
  (let [groupType (getGroupType groupName)
        editingPolicy (getEditingPolicy groupName)
    ]
    (if (not= editingPolicy "Archive")
      (if (.contains (getMemberList groupName) username)
        (if (not= editingPolicy "Editable_Non-Moderated")
          (deleteFromModeratedGroup :username username :groupName groupName :resourceIDMap resourceIDMap :resourceClass resourceClass)
          (deleteFromUnmoderatedGroup :username username :groupName groupName :resourceIDMap resourceIDMap :resourceClass resourceClass)
        )
        (if (= groupType "Anonymous")
          (if (not= editingPolicy "Editable_Non-Moderated")
            (deleteFromModeratedGroup :username username :groupName groupName :resourceIDMap resourceIDMap :resourceClass resourceClass)
            (deleteFromUnmoderatedGroup :username username :groupName groupName :resourceIDMap resourceIDMap :resourceClass resourceClass)
          )
          "Delete Unsuccessful: User is not a member of the group"
        )
      )
      "Delete Unsuccessful: The group is either non-editable or you're trying to cross-publish in a private group"
    )
  )
)

(defn deleteFromPersonalWorkspace
	"Delete nodes from personal workspace"
  	[& {:keys [:username :resourceIDMap :resourceClass]}]
	(gneo/deleteRelations 	:fromNodeLabel [resourceClass] 
							:fromNodeParameters resourceIDMap 
							:relationshipType "GDB_MemberOfWorkspace" 
							:toNodeLabel ["GDB_PersonalWorkspace"] 
							:toNodeParameters {"GDB_DisplayName" username})
	(gneo/deleteRelations 	:fromNodeLabel [resourceClass] 
							:fromNodeParameters resourceIDMap 
							:relationshipType "GDB_CreatedBy" 
							:toNodeLabel ["GDB_PersonalWorkspace"] 
							:toNodeParameters {"GDB_DisplayName" username})
	(gneo/deleteRelations 	:fromNodeLabel [resourceClass] 
							:fromNodeParameters resourceIDMap 
							:relationshipType "GDB_LastModifiedBy" 
							:toNodeLabel ["GDB_PersonalWorkspace"] 
							:toNodeParameters {"GDB_DisplayName" username})
	; (let [resourceUHRID
	;   	(((first (gneo/getNodes :label resourceClass :parameters resourceIDMap)) :properties) "GDB_UHRID")]
	;   	(gneo/editNodeProperties 	:label resourceClass 
	; 	  							:parameters resourceIDMap 
	; 	  							:changeMap {"GDB_UHRID" (deleteWorkspaceFromUHRID :resourceUHRID resourceUHRID :workspaceName username)})
 ;  	)
)

(defn resourceExists
	"Returns true if given workspace contains the given resource else false"
	[& {:keys [:resourceIDMap :resourceClass :workspaceName :workspaceClass]}]
	(let [workspaces
		(map #(((% :end) :properties) "GDB_DisplayName")
			(gneo/getRelations 	:fromNodeLabel [resourceClass]
								:fromNodeParameters resourceIDMap
								:relationshipType "GDB_MemberOfWorkspace"
								:toNodeLabel [workspaceClass]
								:nodeInfo? true
			)
		)]
		(if (empty? workspaces)
			false 
			(if (.contains workspaces workspaceName)
				true
				false
			)
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