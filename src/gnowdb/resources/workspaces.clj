(ns gnowdb.resources.workspaces
  (:require [liberator.core 
            :refer [defresource resource request-method-in]]
            [cheshire.core :refer :all] 
            [gnowdb.core :refer :all]
            [gnowdb.neo4j.gneo :refer :all])) 

(use 'clojure.walk)
 
(defresource prepare-Node-Class [request]
  :service-available? true
  :allowed-methods [:post]
  :handle-created (fn [_] (prepareNodeClass))
  :available-media-types ["application/json"]) 

(defresource create-Abstract-Workspace-Class [request]
  :service-available? true
  :allowed-methods [:post]
  :handle-created (fn [_] (createAbstractWorkspaceClass))
  :available-media-types ["application/json"]) 

(defresource create-Personal-Workspace-Class [request]
  :service-available? true
  :allowed-methods [:post]
  :handle-created (fn [_] (createPersonalWorkspaceClass))
  :available-media-types ["application/json"]) 

(defresource create-Group-Workspace-Class [request]
  :service-available? true
  :allowed-methods [:post]
  :handle-created (fn [_] (createGroupWorkspaceClass))
  :available-media-types ["application/json"]) 


(defresource generate-UHRID [request]
  :service-available? true
  :allowed-methods [:get]
  :handle-ok (fn [_] (let [params (get-in request[:params])] (generateUHRID :label (get-in params [:resourceUHRID]) 
                                                                       		:workspaceName (get-in params[:workspaceName]))))
  :available-media-types ["application/json"])

(defresource delete-Workspace-From-UHRID [request]
  :service-available? true
  :allowed-methods [:delete!]
  :handle-ok (fn [_] (let [params (get-in request[:params])] (deleteWorkspaceFromUHRID  :label (get-in params [:resourceUHRID]) 
                                                                       					:workspaceName (get-in params[:workspaceName]))))
  :available-media-types ["application/json"])
