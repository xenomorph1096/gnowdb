ns gnowdb.routes.workspaces
  (:require [compojure.core :refer :all]
            [gnowdb.resources.workspaces :refer :all]))

(defroutes workspaces-routes

  (context "/api" [] 

    (POST "/prepareNodeClass" request (prepare-Node-Class request))
    (POST "/createAbstractWorkspaceClass" request (create-Abstract-Workspace-Class request))
    (POST "/createPersonalWorkspaceClass" request (create-Personal-Workspace-Class request))
    (POST "/createGroupWorkspaceClass" request (create-Group-Workspace-Class request))
    (GET "/generateUHRID" request (generate-UHRID request))
    (DELETE "/deleteWorkspaceFromUHRID" request (delete-Workspace-From-UHRID request))
    (POST "/instantiateGroupWorkspace" request (instantiate-Group-Workspace request))
    (POST "/instantiatePersonalWorkspace" request (instantiate-Personal-Workspace request))
    (POST "/instantiateDefaultWorkspaces" request (instantiate-Default-Workspaces request))
    (GET "/getAdminList" request (get-Admin-List request))
    (GET "/getMemberList" request (get-Member-List request))
    (GET "/getPublishedResources" request (get-Published-Resources request))
    (GET "/getPendingResources" request (get-Pending-Resources request))
    (GET "/getGroupType" request (get-Group-Type request))
    (GET "/getEditingPolicy" request (get-Editing-Policy request))
    (POST "/setGroupType" request (set-Group-Type request))
    (POST "/setEditingPolicy" request (set-Editing-Policy request))
    (POST "/editLastModified" request (edit-Last-Modified request))
    (GET "/getTypeOfWorkspaces" request (get-Type-Of-Workspaces request))

    (POST "/publishToUnmoderatedGroup" request (publish-To-Unmoderated-Group request))
    (POST "/publishToModeratedGroup" request (publish-To-Moderated-Group request))
    (POST "/publishToAdminOnlyGroup" request (publish-To-Admin-Only-Group request))
    (GET "/crossPublishAllowed?" request (cross-Publish-Allowed? request))

    (POST "/addMemberToGroup" request (add-Member-To-Group request))
    (POST "/addAdminToGroup" request (add-Admin-To-Group request))
    (POST "/publishToGroup" request (publish-To-Group request))
    (POST "/publishToPersonalWorkspace" request (publish-To-Personal-Workspace request))
    (POST "/publishPendingResource" request (publish-Pending-Resource request))
    (DELETE "/moveToTrash" request (move-To-Trash request))
    (POST "/restoreResource" request (restoreResource request))
    (DELETE "/deleteFromUnmoderatedGroup" request (delete-From-Unmoderated-Group request))
    (DELETE "/deleteFromModeratedGroup" request (delete-From-Moderated-Group request))
    (DELETE "/deleteFromGroup" request (delete-From-Group request))
    (DELETE "/deleteFromPersonalWorkspace" request (delete-From-Personal-Workspace request))
    (DELETE "/removeMemberFromGroup" request (remove-Member-From-Group request))
    (DELETE "/removeAdminFromGroup" request (remove-Admin-From-Group request))
    (GET "/resourceExists" request (resource-Exists request))   
  )
)