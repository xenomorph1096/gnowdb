(ns gnowdb.resources.workspaces
  (:require [liberator.core :refer [defresource resource request-method-in]]
            [cheshire.core :refer :all] 
            [gnowdb.core :refer :all]
            [gnowdb.spec.workspaces :refer :all])) 

(use 'clojure.walk)


 
; (defresource prepare-Node-Class [request]
;   :service-available? true
;   :allowed-methods [:post]
;   :handle-created (fn [_] (prepareNodeClass))
;   :available-media-types ["application/json"]) 



; (defresource create-Abstract-Workspace-Class [request]
;   :service-available? true
;   :allowed-methods [:post]
;   :handle-created (fn [_] (createAbstractWorkspaceClass))
;   :available-media-types ["application/json"]) 



; (defresource create-Personal-Workspace-Class [request]
;   :service-available? true
;   :allowed-methods [:post]
;   :handle-created (fn [_] (createPersonalWorkspaceClass))
;   :available-media-types ["application/json"]) 



; (defresource create-Group-Workspace-Class [request]
;   :service-available? true
;   :allowed-methods [:post]
;   :handle-created (fn [_] (createGroupWorkspaceClass))
;   :available-media-types ["application/json"]) 




; (defresource generate-UHRID [request]
;   :service-available? true
;   :allowed-methods [:get]
;   :handle-ok (fn [_] (let [params (get-in request[:params])] (generateUHRID :label (get-in params [:resourceUHRID]) 
;                                                                        		:workspaceName (get-in params[:workspaceName]))))
;   :available-media-types ["application/json"])



; (defresource delete-Workspace-From-UHRID [request]
;   :service-available? true
;   :allowed-methods [:delete]
;   :handle-ok (fn [_] (let [params (get-in request[:params])] (deleteWorkspaceFromUHRID  :label (get-in params [:resourceUHRID]) 
;                                                                        					:workspaceName (get-in params[:workspaceName]))))
;   :available-media-types ["application/json"])



(defresource instantiate-Group-Workspace [request]
    :service-available? true
    :allowed-methods [:post]    
    :handle-created (fn [_] (let [params (get-in request[:params])] (instantiateGroupWorkspace  :groupType (if (contains? params :groupType) (get-in params[:fromNodeParameters]) "Public") 
                                                                                    			:editingPolicy (if (contains? params :editingPolicy) (get-in params[:editingPolicy]) "Editable_Non-Moderated") 
                                                                                    			:displayName (get-in params[:displayName])
                                                                                    			:alternateName (if (contains? params :alternateName) (get-in params[:alternateName]) [])
                                                                                    			:description (if (contains? params :description) (get-in params[:description]) "")
                                                                                    			:createdBy (if (contains? params :createdBy) (get-in params[:createdBy]) "admin")                                                                                    			
                                                                                    			:subGroupOf (if (contains? params :subGroupOf) (get-in params[:subGroupOf]) [])
                                                                                    			:relationshipsOnly? (if (contains? params :relationshipsOnly?) (get-in params[:relationshipsOnly?]) false))))
    :available-media-types ["application/json"])


(defresource instantiate-Personal-Workspace [request]
    :service-available? true
    :allowed-methods [:post]    
    :handle-created (fn [_] (let [params (get-in request[:params])] (instantiatePersonalWorkspace 	:displayName (get-in params[:displayName])
                                                                                    				:alternateName (if (contains? params :alternateName) (get-in params[:alternateName]) [])
                                                                                    				:createdBy (if (contains? params :createdBy) (get-in params[:createdBy]) "admin")            
                                                                                    				:memberOfGroup (if (contains? params :memberOfGroup) (get-in params[:memberOfGroup]) "home")        
                                                                                    				:description (if (contains? params :description) (get-in params[:description]) "")                                                                         			
                                                                                    				:relationshipsOnly? (if (contains? params :relationshipsOnly?) (get-in params[:relationshipsOnly?]) false))))
    :available-media-types ["application/json"])


; (defresource instantiate-Default-Workspaces [request]
;   :service-available? true
;   :allowed-methods [:post]
;   :handle-created (fn [_] (instantiateDefaultWorkspaces))
;   :available-media-types ["application/json"]) 



(defresource get-Admin-List [request]
  :service-available? true
  :allowed-methods [:get]
  :handle-ok (fn [_] (getAdminList (get-in request [:params :groupName])))
  :available-media-types ["application/json"])



(defresource get-Member-List [request]
  :service-available? true
  :allowed-methods [:get]
  :handle-ok (fn [_] (getMemberList (get-in request [:params :groupName])))
  :available-media-types ["application/json"])


(defresource get-Published-Resources [request]
  :service-available? true
  :allowed-methods [:get]
  :handle-ok (fn [_] (getPublishedResources (get-in request [:params :groupName])))
  :available-media-types ["application/json"])



(defresource get-Pending-Resources [request]
  :service-available? true
  :allowed-methods [:get]
  :handle-ok (fn [_] (getPendingResources (get-in request [:params :groupName])))
  :available-media-types ["application/json"])



(defresource get-Group-Type [request]
  :service-available? true
  :allowed-methods [:get]
  :handle-ok (fn [_] (getGroupType (get-in request [:params :groupName])))
  :available-media-types ["application/json"])



(defresource get-Editing-Policy [request]
  :service-available? true
  :allowed-methods [:get]
  :handle-ok (fn [_] (getEditingPolicy (get-in request [:params :groupName])))
  :available-media-types ["application/json"])



(defresource set-Group-Type [request]
    :service-available? true
    :allowed-methods [:post]    
    :handle-created (fn [_] (let [params (get-in request[:params])] (setGroupType 	:groupName (get-in params [:groupName]) 
                                                                                    :adminName (get-in params[:adminName]) 
                                                                                    :groupType (get-in params[:groupType]) )))
    :available-media-types ["application/json"])



(defresource set-Editing-Policy [request]
    :service-available? true
    :allowed-methods [:post]    
    :handle-created (fn [_] (let [params (get-in request[:params])] (setEditingPolicy 	:groupName (get-in params [:groupName]) 
                                                                        	            :adminName (get-in params[:adminName]) 
                                                                            	        :setEditingPolicy (get-in params[:setEditingPolicy]) )))
    :available-media-types ["application/json"])



; (defresource edit-Last-Modified [request]
;     :service-available? true
;     :allowed-methods [:post]    
;     :handle-created (fn [_] (let [params (get-in request[:params])] (editLastModified 	:editor (get-in params[:editor])
;     																					                                           :groupName (get-in params [:groupName]))))
;     :available-media-types ["application/json"])



; (defresource get-Type-Of-Workspaces [request]
;     :service-available? true
;     :allowed-methods [:get]    
;     :handle-ok (fn [_] (let [params (get-in request[:params])] (getTypeOfWorkspaces :resourceIDMap (stringify-keys (get-in params[:resourceIDMap]))  
;     																			                                         	:resourceClass (get-in params [:resourceClass]))))
;     :available-media-types ["application/json"])



; (defresource publish-To-Unmoderated-Group [request]
;     :service-available? true
;     :allowed-methods [:post]    
;     :handle-created (fn [_] (let [params (get-in request[:params])] (publishToUnmoderatedGroup  	:username (get-in params [:username]) 
;     																							                                                :groupName (get-in params [:groupName]) 
;                                                                                   			         	:resourceIDMap (stringify-keys (get-in params[:resourceIDMap]))
;                                                                                  			            :resourceClass (get-in params [:resourceClass]))))
;     :available-media-types ["application/json"])



; (defresource publish-To-Moderated-Group [request]
;     :service-available? true
;     :allowed-methods [:post]    
;     :handle-created (fn [_] (let [params (get-in request[:params])] (publishToModeratedGroup 	  :username (get-in params [:username]) 
;     																							                                              :groupName (get-in params [:groupName]) 
;                                                                                   			       	:resourceIDMap (stringify-keys (get-in params[:resourceIDMap]))
;                                                                                  			          :resourceClass (get-in params [:resourceClass]))))
;     :available-media-types ["application/json"])



; (defresource publish-To-Admin-Only-Group [request]
;     :service-available? true
;     :allowed-methods [:post]    
;     :handle-created (fn [_] (let [params (get-in request[:params])] (publishToAdminOnlyGroup 	:username (get-in params [:username]) 
;     																							                                            :groupName (get-in params [:groupName]) 
;                                                                                   				    :resourceIDMap (stringify-keys (get-in params[:resourceIDMap]))
;                                                                                  			        :resourceClass (get-in params [:resourceClass]))))
;     :available-media-types ["application/json"])









; (defresource publish-To-Admin-Only-Group [request]
;     :service-available? true
;     :allowed-methods [:get]    
;     :handle-ok (fn [_] (let [params (get-in request[:params])] (publishToAdminOnlyGroup  	:resourceIDMap (stringify-keys (get-in params[:resourceIDMap]))
;                                                                                  			:resourceClass (get-in params [:resourceClass])
;                                                                                  			:groupType (get-in params [:groupType]) 
;     																						:groupName (get-in params [:groupName]))))
;     :available-media-types ["application/json"])








(defresource add-Member-To-Group [request]
    :service-available? true
    :allowed-methods [:post]    
    :handle-created (fn [_] (let [params (get-in request[:params])] (addMemberToGroup 	:newMemberName (get-in params [:newMemberName]) 
    																					:groupName (get-in params[:groupName])
                                                                        	            :adminName (get-in params[:adminName]))))                                                                            	        
    :available-media-types ["application/json"])



(defresource add-Admin-To-Group [request]
    :service-available? true
    :allowed-methods [:post]    
    :handle-created (fn [_] (let [params (get-in request[:params])] (addAdminToGroup 	:newAdminName (get-in params [:newAdminName]) 
    																					:groupName (get-in params[:groupName])
                                                                        	            :adminName (get-in params[:adminName]))))                                                                            	        
    :available-media-types ["application/json"])



(defresource publish-To-Group [request]
    :service-available? true
    :allowed-methods [:post]    
    :handle-created (fn [_] (let [params (get-in request[:params])] (publishToGroup 	:username (get-in params [:username]) 
    																					:groupName (get-in params [:groupName]) 
                                                                                  		:resourceIDMap (stringify-keys (get-in params[:resourceIDMap]))
                                                                                 		:resourceClass (get-in params [:resourceClass]))))
    :available-media-types ["application/json"])



(defresource publish-To-Personal-Workspace [request]
    :service-available? true
    :allowed-methods [:post]    
    :handle-created (fn [_] (let [params (get-in request[:params])] (publishToPersonalWorkspace 	:username (get-in params [:username]) 
    																					       		:resourceIDMap (stringify-keys (get-in params[:resourceIDMap]))
                                                                                 					:resourceClass (get-in params [:resourceClass]))))
    :available-media-types ["application/json"])



(defresource publish-Pending-Resource [request]
    :service-available? true
    :allowed-methods [:post]    
    :handle-created (fn [_] (let [params (get-in request[:params])] (publishPendingResource 	:adminName (get-in params [:adminName]) 
    																						                                            	:groupName (get-in params [:groupName]) 
    																					       	                                       :resourceIDMap (stringify-keys (get-in params[:resourceIDMap]))
                                                                                 			      	:resourceClass (get-in params [:resourceClass]))))
    :available-media-types ["application/json"])



(defresource move-To-Trash [request]
    :service-available? true
    :allowed-methods [:delete]    
    :handle-ok (fn [_] (let [params (get-in request[:params])] (moveToTrash 	    :resourceIDMap (stringify-keys (get-in params[:resourceIDMap]))
                                                                                 	:resourceClass (get-in params [:resourceClass]))))
    :available-media-types ["application/json"])




(defresource purge-Trash [request]
    :service-available? true
    :allowed-methods [:delete]    
    :handle-ok (fn [_] (let [params (get-in request[:params])] (purgeTrash        :adminName (get-in params [:adminName]) 
                                                                                  :resourceIDMap (stringify-keys (get-in params[:resourceIDMap]))
                                                                                  :resourceClass (get-in params [:resourceClass]))))
    :available-media-types ["application/json"])




(defresource restore-Resource [request]
    :service-available? true
    :allowed-methods [:post]    
    :handle-created (fn [_] (let [params (get-in request[:params])] (restoreResource 	:resourceIDMap (stringify-keys (get-in params[:resourceIDMap]))
                                                                                 		:resourceClass (get-in params [:resourceClass]))))
    :available-media-types ["application/json"])



; (defresource delete-From-Unmoderated-Group [request]
;     :service-available? true
;     :allowed-methods [:delete]    
;     :handle-ok (fn [_] (let [params (get-in request[:params])] (deleteFromUnmoderatedGroup 	:username (get-in params [:username]) 
;     																						:groupName (get-in params [:groupName]) 
;                                                                                   			:resourceIDMap (stringify-keys (get-in params[:resourceIDMap]))
;                                                                                  			:resourceClass (get-in params [:resourceClass]))))
;     :available-media-types ["application/json"])




; (defresource delete-From-Moderated-Group [request]
;     :service-available? true
;     :allowed-methods [:delete]    
;     :handle-ok (fn [_] (let [params (get-in request[:params])] (deleteFromModeratedGroup 	:username (get-in params [:username]) 
;     																						:groupName (get-in params [:groupName]) 
;                                                                                   			:resourceIDMap (stringify-keys (get-in params[:resourceIDMap]))
;                                                                                  			:resourceClass (get-in params [:resourceClass]))))
;     :available-media-types ["application/json"])



(defresource delete-From-Group [request]
    :service-available? true
    :allowed-methods [:delete]    
    :handle-ok (fn [_] (let [params (get-in request[:params])] (deleteFromGroup 	:username (get-in params [:username]) 
    																				:groupName (get-in params [:groupName]) 
                                                                                  	:resourceIDMap (stringify-keys (get-in params[:resourceIDMap]))
                                                                                 	:resourceClass (get-in params [:resourceClass]))))
    :available-media-types ["application/json"])




(defresource delete-From-Personal-Workspace [request]
    :service-available? true
    :allowed-methods [:delete]    
    :handle-ok (fn [_] (let [params (get-in request[:params])] (deleteFromPersonalWorkspace 	:username (get-in params [:username])     													
                                                                                  				:resourceIDMap (stringify-keys (get-in params[:resourceIDMap]))
                                                                                 				:resourceClass (get-in params [:resourceClass]))))
    :available-media-types ["application/json"])




(defresource remove-Member-From-Group [request]
    :service-available? true
    :allowed-methods [:delete]    
    :handle-ok (fn [_] (let [params (get-in request[:params])] (removeMemberFromGroup 	:memberName (get-in params [:memberName])  
    																					:groupName (get-in params [:groupName])    	
    																					:adminName (get-in params [:adminName]))))												

    :available-media-types ["application/json"])




(defresource remove-Admin-From-Group [request]
    :service-available? true
    :allowed-methods [:delete]    
    :handle-ok (fn [_] (let [params (get-in request[:params])] (removeAdminFromGroup 	:removedAdminName (get-in params [:removedAdminName])  
    																					:groupName (get-in params [:groupName])    	
    																					:adminName (get-in params [:adminName]))))												

    :available-media-types ["application/json"])




(defresource resource-Exists [request]
    :service-available? true
    :allowed-methods [:get]    
    :handle-ok (fn [_] (let [params (get-in request[:params])] (resourceExists 	:resourceIDMap (stringify-keys (get-in params[:resourceIDMap]))
                                                                                :resourceClass (get-in params [:resourceClass])
                                                                                :workspaceName (get-in params [:workspaceName]) 
    																			:workspaceClass (get-in params [:workspaceClass]))))                                                                                  
    :available-media-types ["application/json"])



