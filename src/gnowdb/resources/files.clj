(ns gnowdb.resources.files
  (:require [liberator.core :refer [defresource resource request-method-in]]
            [cheshire.core :refer :all] 
            [gnowdb.core :refer :all]
            [gnowdb.spec.files :refer :all]
            )) 

(use 'clojure.walk)



(defresource get-Data-Directory [request]
	:service-available? true
    :allowed-methods [:get]    
    :handle-ok (fn [_] (let [params (get-in request[:params])] (getDataDirectory (stringify-keys (get-in params[:details])))))
    :available-media-types ["application/json" "text/plain"])



(defresource delete-File-From-Group-Workspace [request]
    :service-available? true
    :allowed-methods [:delete]    
    :handle-ok (fn [_] (let [params (get-in request[:params])] (deleteFileFromGroupWorkspace     :username (get-in params [:username]) 
                                                                                 				 :groupName (get-in params[:groupName])
                                                                                 				 :GDB_MD5 (get-in params [:GDB_MD5]))))
    :available-media-types ["application/json"])



(defresource delete-File-From-Personal-Workspace [request]
    :service-available? true
    :allowed-methods [:delete]    
    :handle-ok (fn [_] (let [params (get-in request[:params])] (deleteFileFromPersonalWorkspace  :username (get-in params [:username])                                                                                 				 
                                                                                 				 :GDB_MD5 (get-in params [:GDB_MD5]))))
    :available-media-types ["application/json"])



(defresource restore-File [request]
    :service-available? true
    :allowed-methods [:post]    
    :handle-created (fn [_] (let [params (get-in request[:params])] (restoreFile 	:GDB_MD5 (get-in params [:GDB_MD5]) 
    																			   	:workspaceClass (get-in params [:workspaceClass]) 
    																			    :workspaceName (get-in params[:workspaceName])
                                                                                   	:username (get-in params [:username]))))
    :available-media-types ["application/json"])



(defresource purge-File [request]
    :service-available? true
    :allowed-methods [:delete]    
    :handle-ok (fn [_] (let [params (get-in request[:params])] (purgeFile 	:adminName (get-in params [:adminName])                                                                                 				 
                                                                            :GDB_MD5 (get-in params [:GDB_MD5]))))
    :available-media-types ["application/json"])



(defresource add-File-To-DB [request]
    :service-available? true
    :allowed-methods [:post]    
    :handle-created (fn [_] (let [params (get-in request[:params])] (addFileToDB 	:fileSrcPath (get-in params [:fileSrcPath]) 
    																			   	:author (get-in params [:author])     																			   
                                                                                   	:memberOfWorkspace (if (contains? params :memberOfWorkspace) (get-in params[:memberOfWorkspace]) []))))
    :available-media-types ["application/json"])



(defresource remove-File-From-DB [request]
    :service-available? true
    :allowed-methods [:delete]    
    :handle-ok (fn [_] (let [params (get-in request[:params])] (removeFileFromDB 	:GDB_MD5 (get-in params [:GDB_MD5]))))
    :available-media-types ["application/json"])








