(ns gnowdb.routes.workspaces
  (:require [compojure.core :refer :all]
            [gnowdb.resources.workspaces :refer :all]
            [cemerick.friend :as friend]
            (cemerick.friend [workflows :as workflows]
                             [credentials :as creds])
            [gnowdb.users :as users :refer (users)] 
    )
  )

(defroutes workspaces-routes

  (context "/api" [] 

    ; (POST "/prepareNodeClass" request (prepare-Node-Class request))

    ; (POST "/createAbstractWorkspaceClass" request (create-Abstract-Workspace-Class request))

    ; (POST "/createPersonalWorkspaceClass" request (create-Personal-Workspace-Class request))

    ; (POST "/createGroupWorkspaceClass" request (create-Group-Workspace-Class request))

    ; (GET "/generateUHRID" request (generate-UHRID request))

    ; (DELETE "/deleteWorkspaceFromUHRID" request (delete-Workspace-From-UHRID request))

    (POST "/instantiateGroupWorkspace" request (friend/authorize #{::users/user} 
                                                (instantiate-Group-Workspace request)))

    (POST "/instantiatePersonalWorkspace" request (friend/authorize #{::users/user} 
                                                (instantiate-Personal-Workspace request)))

    ; (POST "/instantiateDefaultWorkspaces" request (instantiate-Default-Workspaces request))

    (GET "/getAdminList" request (friend/authorize #{::users/user} 
                                    (get-Admin-List request)))

    (GET "/getMemberList" request (friend/authorize #{::users/user} 
                                    (get-Member-List request)))

    (GET "/getPublishedResources" request (friend/authorize #{::users/user} 
                                    (get-Published-Resources request)))

    (GET "/getPendingResources" request (friend/authorize #{::users/user} 
                                    (get-Pending-Resources request)))

    (GET "/getGroupType" request (friend/authorize #{::users/user} 
                                    (get-Group-Type request)))

    (GET "/getEditingPolicy" request (friend/authorize #{::users/user} 
                                    (get-Editing-Policy request)))

    (POST "/setGroupType" request (friend/authorize #{::users/user} 
                                    (set-Group-Type request)))

    (POST "/setEditingPolicy" request (friend/authorize #{::users/user} 
                                    (set-Editing-Policy request)))

    ; (POST "/editLastModified" request (edit-Last-Modified request))

    ; (GET "/getTypeOfWorkspaces" request (get-Type-Of-Workspaces request))

    ; (POST "/publishToUnmoderatedGroup" request (publish-To-Unmoderated-Group request))

    ; (POST "/publishToModeratedGroup" request (publish-To-Moderated-Group request))

    ; (POST "/publishToAdminOnlyGroup" request (publish-To-Admin-Only-Group request))

    ; (GET "/crossPublishAllowed?" request (cross-Publish-Allowed? request))

    (POST "/addMemberToGroup" request (friend/authorize #{::users/user} 
                                    (add-Member-To-Group request)))

    (POST "/addAdminToGroup" request (friend/authorize #{::users/user} 
                                    (add-Admin-To-Group request)))

    (POST "/publishToGroup" request (friend/authorize #{::users/user} 
                                    (publish-To-Group request)))

    (POST "/publishToPersonalWorkspace" request (friend/authorize #{::users/user} 
                                    (publish-To-Personal-Workspace request)))

    (POST "/publishPendingResource" request (friend/authorize #{::users/user} 
                                    (publish-Pending-Resource request)))

    (DELETE "/moveToTrash" request (friend/authorize #{::users/user} 
                                    (move-To-Trash request)))

    (DELETE "/purgeTrash" request (friend/authorize #{::users/user} 
                                    (purge-Trash request)))

    (POST "/restoreResource" request (friend/authorize #{::users/user} 
                                    (restore-Resource request)))

    ; (DELETE "/deleteFromUnmoderatedGroup" request (delete-From-Unmoderated-Group request))

    ; (DELETE "/deleteFromModeratedGroup" request (delete-From-Moderated-Group request))

    (DELETE "/deleteFromGroup" request (friend/authorize #{::users/user} 
                                    (delete-From-Group request)))

    (DELETE "/deleteFromPersonalWorkspace" request (friend/authorize #{::users/user} 
                                    (delete-From-Personal-Workspace request)))

    (DELETE "/removeMemberFromGroup" request (friend/authorize #{::users/user} 
                                    (remove-Member-From-Group request)))

    (DELETE "/removeAdminFromGroup" request (friend/authorize #{::users/user} 
                                    (remove-Admin-From-Group request)))
    
    (GET "/resourceExists" request (friend/authorize #{::users/user} 
                                    (resource-Exists request)))
  )
)