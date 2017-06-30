(ns gnowdb.spec.workspaces
  (:gen-class)
  (:require [gnowdb.neo4j.gneo :as gneo]
            [gnowdb.spec.rcs :as rcs]
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
  (gneo/createClass :className "GDB_SubGroupOf" :classType "RELATION" :isAbstract? false :properties {})
  (gneo/addRelApplicableType :className "GDB_SubGroupOf" :applicationType "Source" :applicableClassName "GDB_GroupWorkspace")
  (gneo/addRelApplicableType :className "GDB_SubGroupOf" :applicationType "Target" :applicableClassName "GDB_GroupWorkspace")
  nil
  )

                                        ; (defn- generateUHRID
                                        ;   [& {:keys [resourceUHRID workspaceName]}]
                                        ;   (str workspaceName "/" resourceUHRID)
                                        ; )

                                        ; (defn- deleteWorkspaceFromUHRID
                                        ;   [& {:keys [resourceUHRID workspaceName]}]
                                        ;   (clojure.string/replace resourceUHRID (str workspaceName "/") "")
                                        ; )

(defn instantiateGroupWorkspace
  "Creates Group Workspaces, the creater is also made an admin by default.
    :groupType can be Public, Private or Anonymous.
    :editingPolicy can be Editable_Admin-Only, Editable_Moderated, Editable_Non-Moderated or Archive.
    :displayName should be the displayName of the group.
    :createdBy should be the name of the user who created the workspace.
    :subGroupOf should be a vector containing the names of the parent groups.

    Eg:
      1: (instantiateGroupWorkspace :displayName \"Gnowledge\") creates a Public, Editable_Non-Moderated, group workspace called Gnowledge in the database, with admin user being the admin
      2: (instantiateGroupWorkspace :displayName \"GNU\" :editingPolicy \"Editable_Moderated\" :subGroupOf [\"Gnowledge\"] :createdBy \"GN\") creates a Public, Editable_Moderated, group workspace called GNU which is a subgroup of Gnowledge
      3: (instantiateGroupWorkspace :displayName \"Gnowledge\" :relationshipsOnly? true) does NOT create a group, but define the relationships of a group if they did not exist already.
  "
  [& {:keys [:groupType :editingPolicy :displayName :alternateName :description :createdBy :subGroupOf :relationshipsOnly?] :or {:groupType "Public" :editingPolicy "Editable_Non-Moderated" :alternateName "[]" :createdBy "admin" :description "" :subGroupOf [] :relationshipsOnly? false}}]
  (if (false? relationshipsOnly?)
    (gneo/createNodeClassInstances :className "GDB_GroupWorkspace" :nodeList    [{
                                                                                  "GDB_DisplayName" displayName
                                                                                  "GDB_GroupType" groupType
                                                                                  "GDB_EditingPolicy" editingPolicy
                                                                                  "GDB_AlternateName" alternateName
                                                                                  "GDB_ModifiedAt" (.toString (new java.util.Date))
                                                                                  "GDB_CreatedAt" (.toString (new java.util.Date))
                                                                                  "GDB_Description" description
                                        ;         "GDB_UHRID" (generateUHRID :resourceUHRID displayName :workspaceName displayName)
                                                                                  }]
                                   )
    )
  (gneo/createRelationClassInstances :className "GDB_CreatedBy" :relList  [{
                                                                            :fromClassName "GDB_GroupWorkspace"
                                                                            :fromPropertyMap {"GDB_DisplayName" displayName}
                                                                            :toClassName "GDB_PersonalWorkspace"
                                                                            :toPropertyMap {"GDB_DisplayName" createdBy}
                                                                            :propertyMap {}
                                                                            }]
                                     )
  (gneo/createRelationClassInstances :className "GDB_LastModifiedBy" :relList   [{
                                                                                  :fromClassName "GDB_GroupWorkspace"
                                                                                  :fromPropertyMap {"GDB_DisplayName" displayName}
                                                                                  :toClassName "GDB_PersonalWorkspace"
                                                                                  :toPropertyMap {"GDB_DisplayName" createdBy}
                                                                                  :propertyMap {}
                                                                                  }]
                                     )
  (gneo/createRelationClassInstances :className "GDB_MemberOfWorkspace" :relList  [{
                                                                                    :fromClassName "GDB_GroupWorkspace"
                                                                                    :fromPropertyMap {"GDB_DisplayName" displayName}
                                                                                    :toClassName "GDB_GroupWorkspace"
                                                                                    :toPropertyMap {"GDB_DisplayName" displayName}
                                                                                    :propertyMap {}
                                                                                    }]
                                     )
  (gneo/createRelationClassInstances :className "GDB_MemberOfGroup" :relList    [{
                                                                                  :fromClassName "GDB_PersonalWorkspace"
                                                                                  :fromPropertyMap {"GDB_DisplayName" createdBy}
                                                                                  :toClassName "GDB_GroupWorkspace"
                                                                                  :toPropertyMap {"GDB_DisplayName" displayName}
                                                                                  :propertyMap {}
                                                                                  }]
                                     )
  (gneo/createRelationClassInstances :className "GDB_AdminOfGroup" :relList     [{
                                                                                  :fromClassName "GDB_PersonalWorkspace"
                                                                                  :fromPropertyMap {"GDB_DisplayName" createdBy}
                                                                                  :toClassName "GDB_GroupWorkspace"
                                                                                  :toPropertyMap {"GDB_DisplayName" displayName}
                                                                                  :propertyMap {}
                                                                                  }]
                                     )
  (doall
   (map 
    (fn [parentGroupName] 
      (println (gneo/createRelationClassInstances :className "GDB_SubGroupOf" :relList    [{
                                                                                            :fromClassName "GDB_GroupWorkspace"
                                                                                            :fromPropertyMap {"GDB_DisplayName" displayName}
                                                                                            :toClassName "GDB_GroupWorkspace"
                                                                                            :toPropertyMap {"GDB_DisplayName" parentGroupName}
                                                                                            :propertyMap {}
                                                                                            }]
                                                  )) 
      )
    subGroupOf
    )
   )
  (rcs/instantiateResourceRCS :resourceIDMap {"GDB_DisplayName" displayName} :resourceClass "GDB_GroupWorkspace")
  nil
  )

(defn instantiatePersonalWorkspace
  "Creates Personal Workspace, who is marked as being created by the admin and is a member of the group home by default.
    :displayName should be the name of the user
    :createdBy should be the name of the user who created this one, default is admin
    :memberOfGroup should be the name of the Group the user is a member of, default is home

    Eg:
      1: (instantiatePersonalWorkspace :displayName \"GN\") creates a user called GN who is a member of the group home. GN is marked as being created by admin.
      2: (instantiatePersonalWorkspace :displayName \"Lex\" :memberOfGroup \"Gnowledge\") creates a user Lex, who is a member of Gnowledge. Ray is marked as being created by admin.
      3: (instantiateGroupWorkspace :displayName \"Anant\" :memberOfGroup \"Gnowledge\" :createdBy \"GN\") created user Anant, who is a member of Gnowledge. Anant is marked as being created by GN.
  "
  [& {:keys [:displayName :alternateName :createdBy :memberOfGroup :description :relationshipsOnly?] :or {:alternateName "[]" :createdBy "admin" :memberOfGroup "home" :description "" :relationshipsOnly? false}}]
  (if (false? relationshipsOnly?)
    (gneo/createNodeClassInstances :className "GDB_PersonalWorkspace" :nodeList   [{
                                                                                    "GDB_DisplayName" displayName
                                                                                    "GDB_GroupType" "Public"
                                                                                    "GDB_EditingPolicy" "Editable_Admin-Only"
                                                                                    "GDB_AlternateName" alternateName
                                                                                    "GDB_ModifiedAt" (.toString (new java.util.Date))
                                                                                    "GDB_CreatedAt" (.toString (new java.util.Date))
                                                                                    "GDB_Description" description
                                        ;       "GDB_UHRID" (generateUHRID :resourceUHRID displayName :workspaceName displayName)
                                                                                    }]
                                   )
    )
  (gneo/createRelationClassInstances :className "GDB_CreatedBy" :relList  [{
                                                                            :fromClassName "GDB_PersonalWorkspace"
                                                                            :fromPropertyMap {"GDB_DisplayName" displayName}
                                                                            :toClassName "GDB_PersonalWorkspace"
                                                                            :toPropertyMap {"GDB_DisplayName" createdBy}
                                                                            :propertyMap {}
                                                                            }]
                                     )
  (gneo/createRelationClassInstances :className "GDB_LastModifiedBy" :relList   [{
                                                                                  :fromClassName "GDB_PersonalWorkspace"
                                                                                  :fromPropertyMap {"GDB_DisplayName" displayName}
                                                                                  :toClassName "GDB_GroupWorkspace"
                                                                                  :toPropertyMap {"GDB_DisplayName" createdBy}
                                                                                  :propertyMap {}
                                                                                  }]
                                     )
  (gneo/createRelationClassInstances :className "GDB_MemberOfWorkspace" :relList  [{
                                                                                    :fromClassName "GDB_PersonalWorkspace"
                                                                                    :fromPropertyMap {"GDB_DisplayName" displayName}
                                                                                    :toClassName "GDB_PersonalWorkspace"
                                                                                    :toPropertyMap {"GDB_DisplayName" displayName}
                                                                                    :propertyMap {}
                                                                                    }]
                                     )
  (gneo/createRelationClassInstances :className "GDB_MemberOfGroup" :relList    [{
                                                                                  :fromClassName "GDB_PersonalWorkspace"
                                                                                  :fromPropertyMap {"GDB_DisplayName" displayName}
                                                                                  :toClassName "GDB_GroupWorkspace"
                                                                                  :toPropertyMap {"GDB_DisplayName" memberOfGroup}
                                                                                  :propertyMap {}
                                                                                  }]
                                     )
  (rcs/instantiateResourceRCS :resourceIDMap {"GDB_DisplayName" displayName} :resourceClass "GDB_PersonalWorkspace")
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
  (instantiateGroupWorkspace  :displayName "TRASH" 
                              :groupType "Public"
                              :editingPolicy "Editable_Non-Moderated"
                              )
  )

(defn getAdminList
  "Gives the admin list for a particular group workspace, given by the group's name
  Eg:
    1: (getAdminList \"Gnowledge\") gives the admin list for the group Gnowledge
  "
  [groupName]
  (map #(((% :start) :properties) "GDB_DisplayName")
       (gneo/getRelations :toNodeLabels ["GDB_GroupWorkspace"] 
                          :toNodeParameters {"GDB_DisplayName" groupName}
                          :relationshipType "GDB_AdminOfGroup"
                          :nodeInfo? true
                          )
       )
  )

(defn getMemberList
  "Gives the member list for a particular group workspace, given by the group's name.
  Eg:
    1: (getMemberList \"Gnowledge\") gives the member list for the group Gnowledge
  "
  [groupName]
  (map #(((% :start) :properties) "GDB_DisplayName")
       (gneo/getRelations :toNodeLabels ["GDB_GroupWorkspace"] 
                          :toNodeParameters {"GDB_DisplayName" groupName}
                          :relationshipType "GDB_MemberOfGroup"
                          :nodeInfo? true
                          )
       )
  )

(defn getPublishedResources
  "Gives the GDB_DisplayName of published resources in the group, needs the group's name as argument.
  Eg:
    1: (getPublishedResources \"Gnowledge\") gives published resources in the group Gnowledge.
  "
  [groupName]
  (map #(((% :start) :properties) "GDB_DisplayName")
       (gneo/getRelations :toNodeLabels ["GDB_GroupWorkspace"] 
                          :toNodeParameters {"GDB_DisplayName" groupName}
                          :relationshipType "GDB_MemberOfWorkspace"
                          :nodeInfo? true
                          )
       )
  )

(defn getPendingResources
  "Gives the GDB_DisplayName of pending resources in the group, needs the group's name as argument.
  Eg:
    1: (getPendingResources \"Gnowledge\") gives pending resources in the group Gnowledge.
  "
  [groupName]
  (map #((% :start) :properties)
       (gneo/getRelations :toNodeLabels ["GDB_GroupWorkspace"] 
                          :toNodeParameters {"GDB_DisplayName" groupName}
                          :relationshipType "GDB_PendingReview"
                          :nodeInfo? true
                          )
       )
  )

(defn getGroupType
  "Gives the group type of the group, needs the group's name as argument. The group type can be one of public, private, or anonymous.
  Eg:
    1: (getGroupType \"Gnowledge\") gives group type of group Gnowledge.
  "
  [groupName]
  (((first (gneo/getNodes 
            :labels ["GDB_GroupWorkspace"] 
            :parameters  {
                          "GDB_DisplayName" groupName
                          }
            )) :properties) "GDB_GroupType")
  )

(defn getEditingPolicy
  "Gives the editing policy of the group, needs the group's name as argument. The editing policy can be one of Editable_Moderated, Editable_Non-Moderated, Editable_Admin-Only or Archive.
  Eg:
    1: (getEditingPolicy \"Gnowledge\") gives editing policy of group Gnowledge.
  "
  [groupName]
  (((first (gneo/getNodes 
            :labels ["GDB_GroupWorkspace"] 
            :parameters  {
                          "GDB_DisplayName" groupName
                          }
            )) :properties) "GDB_EditingPolicy")
  )

(defn setGroupType
  "Sets the group type of the group, needs the group's name as argument. The group type can be one of public, private, or anonymous.
  Eg:
    1: (getGroupType \"Gnowledge\") sets group type of group Gnowledge.
  "
  [& {:keys [groupName adminName groupType]}]
  (if (.contains (getAdminList groupName) adminName)
    (gneo/editNodeProperties :labels ["GDB_GroupWorkspace"] :parameters {"GDB_DisplayName" groupName} :changeMap {"GDB_GroupType" groupType})
    )
  nil
  )

(defn setEditingPolicy
  "Sets the editing policy of the group, needs the group's name as argument. The editing policy can be one of Editable_Moderated, Editable_Non-Moderated, Editable_Admin-Only or Archive.
  Eg:
    1: (getEditingPolicy \"Gnowledge\") sets editing policy of group Gnowledge.
  "
  [& {:keys [groupName adminName editingPolicy]}]
  (if (.contains (getAdminList groupName) adminName)
    (gneo/editNodeProperties :labels ["GDB_GroupWorkspace"] :parameters {"GDB_DisplayName" groupName} :changeMap {"GDB_GroupType" editingPolicy})
    )
  nil
  )

(defn- editLastModified
  "Changes the last modified credentials for a group workspace and calls on RCS with the accumulated changes."
  [& {:keys [:editor :groupName]}]
  (rcs/updateLastModified :editor editor :resourceClass "GDB_GroupWorkspace" :resourceIDMap {"GDB_DisplayName" groupName})
  )

(defn- getTypeOfWorkspaces
  "Determines types of workspaces a resource is published to. 
   Requires an ID Map of the resource that uniquely identifies it, and its class
  "
  [& {:keys [:resourceIDMap :resourceClass]}]
  (into #{} 
        (map #(((% :end) :properties) "GDB_GroupType")
             (gneo/getRelations  :fromNodeLabels [resourceClass]
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
                                        ;    (((first (gneo/getNodes :labels [resourceClass] :parameters resourceIDMap)) :properties) "GDB_UHRID")]
                                        ;  (gneo/editNodeProperties  :labels [resourceClass] 
                                        ;                :parameters resourceIDMap 
                                        ;                :changeMap {"GDB_UHRID" (generateUHRID  :resourceUHRID resourceUHRID 
                                        ;                                    :workspaceName groupName)})
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
  "Publish nodes to admin only groups i.e. with admin check and no pendencies added"
  [& {:keys [:username :groupName :resourceIDMap :resourceClass]}]
  (if (.contains (getAdminList groupName) username)
    (do
      (publishToUnmoderatedGroup :username username :groupName groupName :resourceIDMap resourceIDMap :resourceClass resourceClass)
      (editLastModified :editor username :groupName groupName)
      )
    )
  )

(defn crossPublishAllowed?
  "Determines whether cross-publication is allowed for a particular resource
    :resourceIDMap takes a map that can uniquely identify a resource in the database.
    :resourceClass takes a string class name of the resource
    :groupName name of the group to which the file needs to be published to
    :groupType (OPTIONAL) type of the group, need not be given
  "
  [& {:keys [:resourceIDMap :resourceClass :groupType :groupName]}]
  (let  [
         workspaceTypes (getTypeOfWorkspaces resourceIDMap resourceClass)
         groupType (if groupType groupType (getGroupType groupName))
         ]
    (if (and (= workspaceTypes #{"Private"}) (not= groupType "Private") (= groupName "TRASH"))
      false
      true
      )
    )
  )

(defn addMemberToGroup
  "Adds a member to group workspace, an admin is required to add members to private groups and no members can be added to anonymous groups
    :newMemberName the name of the member to be added to the groupType
    :groupName the name of the group to which the member needs to be added
    :adminName (OPTIONAL) the name of the admin authorizing the join request. Needed only for private groups!

    Eg:
      1: (addMemberToGroup :newMemberName \"Bruce\" :groupName \"Gnowledge\") adds Bruce to the group Gnowledge
      2: (addMemberToGroup :newMemberName \"Alfred\" :groupName \"Butlers\" :adminName \"Bruce\") adds Alfred to the Butlers group which is managed by Bruce.
  "
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
        (if adminName
          (editLastModified :groupName groupName :editor adminName)
          )
        nil
        )
      {:results [] :summary {:summaryMap {} :summaryString "Membership could not be created, either the group is Anonymous or the user does not have admin permissions"}}
      )
    )
  )

(defn addAdminToGroup
  "Adds an Administrator to a group workspace, the admin should already be a member to the group
    :newAdminName The name of the member who is to be promoted to admin status
    :groupName the name of the group in which the promotion is to be done
    :adminName the name of the admin authorizing the promotion should be here

    Eg:
      1: (addAdminToGroup :newAdminName \"Alfred\" :groupName \"Butlers\" :adminName \"Bruce\") adds Alfred as an admin to the Butlers group, Alfred can now manage all other butlers.
  "
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
  "Publish resources to a Group Workspace in accordance to the editing policy and group type of the group provided
    :username The display name of the user publishing resources to the group
    :groupName The display name of the group to which the resources are being published
    :resourceIDMap An ID map of the resource that is to be published. The resource should be uniquely identifiable.
    :resourceClass The class of the resource that is to be published.
    Eg:
      1: (publishToGroup :username \"Gordon\" :groupName \"GCPD\" :resourceIDMap {\"CriminalName\" \"???\" \"Alias\" \"JOKER\"} :resourceClass \"APBs\") adds an APB to the group GCPD against with the details present in resourceIDMap
  "
  [& {:keys [:username :groupName :resourceIDMap :resourceClass]}]
  (let [groupType (getGroupType groupName)
        editingPolicy (getEditingPolicy groupName)
        ]
    (if (and (not= editingPolicy "Archive") (crossPublishAllowed? :resourceIDMap resourceIDMap :resourceClass resourceClass :groupType groupType :groupName groupName))
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
  "Publish resources to personal workspace
    :username The display name of the user publishing resources to the group
    :resourceIDMap An ID map of the resource that is to be published. The resource should be uniquely identifiable.
    :resourceClass The class of the resource that is to be published.
    Eg:
      1: (publishToPersonalWorkspace :username \"Darkseid\" :resourceIDMap {\"PlanetID\" \"Earth:52\"} :resourceClass \"Planets\")
  "
  [& {:keys [:username :resourceIDMap :resourceClass]}]
  (gneo/createRelationClassInstances :className "GDB_MemberOfWorkspace" :relList  [{
                                                                                    :fromClassName resourceClass
                                                                                    :fromPropertyMap resourceIDMap
                                                                                    :toClassName "GDB_PersonalWorkspace"
                                                                                    :toPropertyMap {"GDB_DisplayName" username}
                                                                                    :propertyMap {}
                                                                                    }]
                                     )
  (rcs/updateLastModified :editor username :resourceIDMap {"GDB_DisplayName" username} :resourceClass "GDB_PersonalWorkspace")
                                        ; (let [resourceUHRID
                                        ;    (((first (gneo/getNodes :labels [resourceClass] :parameters resourceIDMap)) :properties) "GDB_UHRID")]
                                        ;  (gneo/editNodeProperties  :labels [resourceClass] 
                                        ;                :parameters resourceIDMap 
                                        ;                :changeMap {"GDB_UHRID" (generateUHRID  :resourceUHRID resourceUHRID 
                                        ;                                    :workspaceName username)})
                                        ; )
  )

(defn publishPendingResource
  "Publishes Pending Resources to Group, which were added by non-admin members to Editable_Moderated groups
    :adminName The display name of the admin publishing resources to the group
    :groupName The display name of the group to which the resources are being published
    :resourceIDMap An ID map of the resource that is to be published. The resource should be uniquely identifiable.
    :resourceClass The class of the resource that is to be published.

    Eg:
      1: (publishPendingResource :adminName \"Bruce\" :groupName \"Utility\" :resourceIDMap {\"Inventor\" \"Lucius\" \"ID\" \"Knightfall\"} :resourceClass \"Protocols\") 
          Adds a protocol resource to the Utility group which was added by a non admin user earlier, but is now approved by an admin.
  "
  [& {:keys [:adminName :groupName :resourceIDMap :resourceClass]}]
  (gneo/deleteRelation
   :toNodeLabels ["GDB_GroupWorkspace"]
   :toNodeProperties {"GDB_DisplayName" groupName}
   :relationshipType "GDB_PendingReview"
   :fromNodeLabels [resourceClass]
   :fromNodeProperties resourceIDMap
   )
  (publishToGroup :username adminName :groupName groupName :resourceIDMap resourceIDMap :resourceClass resourceClass)
  )

(defn moveToTrash
  "Moves the resource into the TRASH workspace i.e. adds a GDB_MemberOfWorkspace relationship between
  the given resource and the TRASH workspace.
    :resourceIDMap contains the key-value pair which uniquely identifies the resource.
    :resourceClass is the class to which the resource belongs.
  Eg:
      1: (moveToTrash :resourceIDMap {\"GDB_DisplayName\" \"Lamborghini\"} :resourceClass \"GDB_Car\")"
  [& {:keys [:resourceIDMap :resourceClass]}]
  (gneo/createRelationClassInstances :className "GDB_MemberOfWorkspace" :relList  [{
                                                                                    :fromClassName resourceClass
                                                                                    :fromPropertyMap resourceIDMap
                                                                                    :toClassName "GDB_GroupWorkspace"
                                                                                    :toPropertyMap {"GDB_DisplayName" "TRASH"}
                                                                                    :propertyMap {}
                                                                                    }]
                                     )
  )

(defn purgeTrash
  "Removes the resource from the TRASH workspace by deleting the resource instance.
    :adminName should be the name of the admin of the TRASH workspace for the resource to be purged.
    :resourceIDMap contains the key-value pair which uniquely identifies the resource.
    :resourceClass is the class to which the resource belongs.
  Eg:
      1: (purgeTrash :adminName \"Stark\" :resourceIDMap {\"GDB_DisplayName\" \"Lamborghini\"} :resourceClass \"GDB_Car\")"
  [& {:keys [:adminName :resourceIDMap :resourceClass]}]
  (let [admins (getAdminList "TRASH")]
    (if (.contains admins adminName)
      (gneo/deleteDetachNodes   :labels [resourceClass] 
                                :parameters resourceIDMap
                                )
      (println "Given user is not an admin of the TRASH workspace.")  
      )
    )
  )

(defn restoreResource
  "Restores a resource by removing from TRASH i.e. deleting the GDB_MemberOfWorkspace relationship
  between the resource and the TRASH workspace and adding the resource to the given workspace by 
  creating a GDB_MemberOfWorkspace relationship between the resource and the workspace.
  Two sets of inputs possible:
  
    :workspaceClass is GDB_GroupWorkspace.
    :username is the name of the user who is adding the resource to the given GroupWorkspace.
    :workspaceName is the name of the workspace to which the resource needs to be added.
    :resourceIDMap contains the key-value pair which uniquely identifies the resource.
    :resourceClass is the class to which the resource belongs.

    :workspaceClass is GDB_PersonalWorkspace.
    :username not required.
    :workspaceName is the name of the workspace to which the resource needs to be added.
    :resourceIDMap contains the key-value pair which uniquely identifies the resource.
    :resourceClass is the class to which the resource belongs.
  Eg:
      1: (restoreResource :resourceIDMap {\"GDB_DisplayName\" \"Lamborghini\"} :resourceClass \"GDB_Car\" :workspaceClass \"GDB_GroupWorkspace\" :workspaceName \"CheapCars\" :username \"Loki\")
      2: (restoreResource :resourceIDMap {\"GDB_DisplayName\" \"Lamborghini\"} :resourceClass \"GDB_Car\" :workspaceClass \"GDB_PersonalWorkspace\" :workspaceName \"Loki\")"
  [& {:keys [:resourceIDMap :resourceClass :workspaceClass :workspaceName :username]}]
  (gneo/deleteRelation
   :toNodeLabels ["GDB_GroupWorkspace"]
   :toNodeParameters {"GDB_DisplayName" "TRASH"}
   :relationshipType "GDB_MemberOfWorkspace"
   :fromNodeLabels [resourceClass]
   :fromNodeParameters resourceIDMap
   )
  (cond 
    (= workspaceClass "GDB_GroupWorkspace") (publishToGroup :username username :groupName workspaceName :resourceIDMap resourceIDMap :resourceClass resourceClass)
    (= workspaceClass "GDB_PersonalWorkspace") (publishToPersonalWorkspace :username username :resourceIDMap resourceIDMap :resourceClass resourceClass)
    )         
  )

(defn- deleteFromUnmoderatedGroup
  "Deletes a resource from an unmoderated group."
  [& {:keys [:username :groupName :resourceIDMap :resourceClass]}]
  (gneo/deleteRelation   :fromNodeLabels [resourceClass]
                          :fromNodeParameters resourceIDMap 
                          :relationshipType "GDB_MemberOfWorkspace" 
                          :toNodeLabels ["GDB_GroupWorkspace"] 
                          :toNodeParameters {"GDB_DisplayName" groupName})
  (if (empty? (gneo/getRelations :fromNodeLabels [resourceClass]
                                 :fromNodeParameters resourceIDMap
                                 :relationshipType "GDB_MemberOfWorkspace"
                                 ))
    (moveToTrash :resourceIDMap resourceIDMap :resourceClass resourceClass)
    )
                                        ; (let [resourceUHRID
                                        ;     (((first (gneo/getNodes :labels [resourceClass] :parameters resourceIDMap)) :properties) "GDB_UHRID")]
                                        ;     (gneo/editNodeProperties  :labels [resourceClass] 
                                        ;                   :parameters resourceIDMap 
                                        ;                   :changeMap {"GDB_UHRID" (deleteWorkspaceFromUHRID :resourceUHRID resourceUHRID :workspaceName groupName)})
                                        ;    )
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
  "Deletes resources from a group workspace.
    :username is the name of the user deleting the file.
    :groupName is the name of the group workspace.
    :resourceIDMap contains the key-value pair which uniquely identifies the resource.
    :resourceClass is the class to which the resource belongs.
  Eg:
      1: (deleteFromGroup :resourceIDMap {\"GDB_DisplayName\" \"Lamborghini\"} :resourceClass \"GDB_Car\" :groupName \"CheapCars\" :username \"Loki\")"
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
  "Deletes resources from a personal workspace.
    :username is the name of the user .
    :resourceIDMap contains the key-value pair which uniquely identifies the resource.
    :resourceClass is the class to which the resource belongs.
  Eg:
      1: (deleteFromPersonalWorkspace :username \"Dexter\" :resourceIDMap {\"GDB_DisplayName\" \"Lamborghini\"} :resourceClass \"GDB_Car\")"
  [& {:keys [:username :resourceIDMap :resourceClass]}]
  (gneo/deleteRelation   :fromNodeLabels [resourceClass] 
                          :fromNodeParameters resourceIDMap 
                          :relationshipType "GDB_MemberOfWorkspace" 
                          :toNodeLabels ["GDB_PersonalWorkspace"] 
                          :toNodeParameters {"GDB_DisplayName" username})
  (gneo/deleteRelation   :fromNodeLabels [resourceClass] 
                          :fromNodeParameters resourceIDMap 
                          :relationshipType "GDB_CreatedBy" 
                          :toNodeLabels ["GDB_PersonalWorkspace"] 
                          :toNodeParameters {"GDB_DisplayName" username})
  (gneo/deleteRelation   :fromNodeLabels [resourceClass] 
                          :fromNodeParameters resourceIDMap 
                          :relationshipType "GDB_LastModifiedBy" 
                          :toNodeLabels ["GDB_PersonalWorkspace"] 
                          :toNodeParameters {"GDB_DisplayName" username})
  (if (empty? (gneo/getRelations :fromNodeLabels [resourceClass]
                                 :fromNodeParameters resourceIDMap
                                 :relationshipType "GDB_MemberOfWorkspace"
                                 ))
    (moveToTrash :resourceIDMap resourceIDMap :resourceClass resourceClass)
    )
  (rcs/updateLastModified :editor username :resourceIDMap {"GDB_DisplayName" username} :resourceClass "GDB_PersonalWorkspace")
                                        ; (let [resourceUHRID
                                        ;     (((first (gneo/getNodes :labels [resourceClass] :parameters resourceIDMap)) :properties) "GDB_UHRID")]
                                        ;     (gneo/editNodeProperties  :labels [resourceClass] 
                                        ;                   :parameters resourceIDMap 
                                        ;                   :changeMap {"GDB_UHRID" (deleteWorkspaceFromUHRID :resourceUHRID resourceUHRID :workspaceName username)})
                                        ;    )
  )

(defn removeMemberFromGroup
  "Removes a member from the given groupworkspace.
    :memberName is the name of the member who needs to be removed.
    :groupName is the name of the group from which the member is to be deleted.
    :adminName is the name of the user removing the member from the group.
    For member to be removed, adminName should be the name of an admin of the group.
  Eg:
      1: (removeMemberFromGroup :memberName \"Tony\" :groupName \"Superheroes\" :adminName \"Steve\")"
  [& {:keys [:memberName :groupName :adminName]}]
  (let [admins (getAdminList groupName)
        members (getMemberList groupName)]
    (if (and (.contains admins adminName) (.contains members memberName))
      (do
        (gneo/deleteRelation   :fromNodeLabels ["GDB_PersonalWorkspace"]
                                :fromNodeParameters {"GDB_DisplayName" memberName} 
                                :relationshipType "GDB_MemberOfGroup" 
                                :toNodeLabels ["GDB_GroupWorkspace"] 
                                :toNodeParameters {"GDB_DisplayName" groupName})
        (gneo/deleteRelation   :fromNodeLabels ["GDB_PersonalWorkspace"]
                                :fromNodeParameters {"GDB_DisplayName" memberName} 
                                :relationshipType "GDB_AdminOfGroup" 
                                :toNodeLabels ["GDB_GroupWorkspace"] 
                                :toNodeParameters {"GDB_DisplayName" groupName})
        (editLastModified :groupName groupName :editor adminName)
        )
      {:results [] :summary {:summaryMap {} :summaryString "Member could not be removed, either the he/she is not a member of the group or the user does not have admin permissions"}}
      )
    )
  nil
  )

(defn removeAdminFromGroup
  "Removes an admin from the given groupworkspace.
    :removedAdminName is the name of the admin whose admin permissions are to be removed.
    :groupName is the name of the group for which the admin is to be removed.
    :adminName is the name of the user who is removing the admin permissions of the removedAdminName.
    adminName should be the name of an admin of the group for the admin permissions to be removed.
  Eg:
      1: (removeAdminFromGroup :removedAdminName \"Tony\" :groupName \"Superheroes\" :adminName \"Steve\")"
  [& {:keys [:removedAdminName :groupName :adminName]}]
  (let [admins (getAdminList groupName)]
    (if (.contains admins adminName)
      (gneo/deleteRelation   :fromNodeLabels ["GDB_PersonalWorkspace"]
                              :fromNodeParameters {"GDB_DisplayName" removedAdminName} 
                              :relationshipType "GDB_AdminOfGroup" 
                              :toNodeLabels ["GDB_GroupWorkspace"] 
                              :toNodeParameters {"GDB_DisplayName" groupName})
      (editLastModified :groupName groupName :editor adminName)
      )
    {:results [] :summary {:summaryMap {} :summaryString "Admin permissions could not be removed,the user does not have admin permissions"}}
    )
  nil
  )

(defn resourceExists
  "Returns true if given workspace contains the given resource else false.
    :workspaceClass is the class name of the workspace(GDB_PersonalWorkspace or GDB_GroupWorkspace).
    :workspaceName is the name of the workspace for which the existence of the resource is to be checked.
    :resourceIDMap contains the key-value pair which uniquely identifies the resource.
    :resourceClass is the class name to which the resource belongs.
  Eg:
      1: (resourceExists :resourceIDMap {\"GDB_DisplayName\" \"Jarvis\"} :resourceClass \"Bot\" :workspaceName \"Superheroes\" :workspaceClass \"GDB_GroupWorkspace\")
      2: (resourceExists :resourceIDMap {\"GDB_DisplayName\" \"Jarvis\"} :resourceClass \"Bot\" :workspaceName \"Tony\" :workspaceClass \"GDB_PersonalWorkspace\")"
  [& {:keys [:resourceIDMap :resourceClass :workspaceName :workspaceClass]}]
  (let [workspaces
        (map #(((% :end) :properties) "GDB_DisplayName")
             (gneo/getRelations  :fromNodeLabels [resourceClass]
                                 :fromNodeParameters resourceIDMap
                                 :relationshipType "GDB_MemberOfWorkspace"
                                 :toNodeLabels [workspaceClass]
                                 :toNodeParameters {"GDB_DisplayName" workspaceName}
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
  "Initializer function for all the default classes and instances."
  []
  (prepareNodeClass)
  (createAbstractWorkspaceClass)
  (createPersonalWorkspaceClass)
  (createGroupWorkspaceClass)
  (instantiateDefaultWorkspaces)
  )
