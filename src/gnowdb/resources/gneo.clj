(ns gnowdb.resources.gneo 
  (:require [liberator.core 
            :refer [defresource resource request-method-in]]
            [cheshire.core :refer :all] 
            [gnowdb.core :refer :all]
            [gnowdb.neo4j.gneo :refer :all])) 
(use 'clojure.walk)
 

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
 

(defresource generate-UUID [request]
  :service-available? true
  :allowed-methods [:get]
  :handle-ok (fn [_] (generateUUID))
  :available-media-types ["application/json"]) 

(defresource get-All-Labels [request]
  :service-available? true
  :allowed-methods [:get]
  :handle-ok (fn [_] (getAllLabels))
  :available-media-types ["application/json"])

(defresource get-All-Nodes [request]
  :service-available? true
  :allowed-methods [:get]
  :handle-ok (fn [_] (getAllNodes))
  :available-media-types ["application/json"])

(defresource create-New-Node [request]
    :service-available? true
    :allowed-methods [:post]    
    :handle-created (fn [_] (let [params (get-in request[:params])] (createNewNode :labels (get-in params [:labels]) 
                                                                                  :parameters (if (contains? params :parameters) (stringify-keys (get-in params[:parameters])) {})  
                                                                                  :execute? (if (contains? params :execute?) (get-in params [:execute?]) true) 
                                                                                  :unique? (if (contains? params :unique?) (get-in params [:unique?]) false) )))
    :available-media-types ["application/json"])

(defresource get-Nodes [request]
  :service-available? true
  :allowed-methods [:get]
  :handle-ok (fn [_] (let [params (get-in request[:params])] (getNodes :labels (get-in params [:labels]) 
                                                                       :parameters (if (contains? params :parameters) (stringify-keys (get-in params[:parameters])) {})
                                                                       :execute? (if (contains? params :execute?) (get-in params [:execute?]) true) )))
  :available-media-types ["application/json"])

(defresource get-Relations [request]
    :service-available? true
    :allowed-methods [:get]    
    :handle-ok (fn [_] (let [params (get-in request[:params])] (getRelations    :fromNodeLabels (if (contains? params :fromNodeLabels) (get-in params [:fromNodeLabels]) "") 
                                                                                :fromNodeParameters (if (contains? params :fromNodeParameters) (stringify-keys (get-in params[:fromNodeParameters])) {}) 
                                                                                :relationshipType (if (contains? params :relationshipType) (get-in params [:relationshipType]) "") 
                                                                                :relationshipParameters (if (contains? params :relationshipParameters) (stringify-keys (get-in params[:relationshipParameters])) {}) 
                                                                                :toNodeLabels (if (contains? params :toNodeLabels) (get-in params [:toNodeLabels]) "") 
                                                                                :toNodeParameters (if (contains? params :toNodeParameters) (stringify-keys (get-in params[:toNodeParameters])) {}) 
                                                                                :execute? (if (contains? params :execute?) (get-in params [:execute?]) true) 
                                                                                :unique? (if (contains? params :unique?) (get-in params [:unique?]) false) )))
    :available-media-types ["application/json"])

(defresource create-Relation [request]
    :service-available? true
    :allowed-methods [:post]    
    :handle-created (fn [_] (let [params (get-in request[:params])] (createRelation  :fromNodeLabels (get-in params [:fromNodeLabels]) 
                                                                                    :fromNodeParameters (if (contains? params :fromNodeParameters) (stringify-keys (get-in params[:fromNodeParameters])) {}) 
                                                                                    :relationshipType (get-in params [:relationshipType]) 
                                                                                    :relationshipParameters (if (contains? params :relationshipParameters) (stringify-keys (get-in params[:relationshipParameters])) {}) 
                                                                                    :toNodeLabels (get-in params [:toNodeLabels]) 
                                                                                    :toNodeParameters (if (contains? params :toNodeParameters) (stringify-keys (get-in params[:toNodeParameters])) {}) 
                                                                                    :execute? (if (contains? params :execute?) (get-in params [:execute?]) true) 
                                                                                    :unique? (if (contains? params :unique?) (get-in params [:unique?]) false) )))
    :available-media-types ["application/json"])



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defresource create-NC-Constraints [request]
    :service-available? true
    :allowed-methods [:post]    
    :handle-created (fn [_] (let [params (get-in request[:params])] (if (contains? params :execute?) (createNCConstraints :execute? (get-in params [:execute?])) (createNCConstraints)) ))
    :available-media-types ["application/json"])


(defresource create-CAT-Constraints [request]
    :service-available? true
    :allowed-methods [:post]    
    :handle-created (fn [_] (let [params (get-in request[:params])] (if (contains? params :execute?) (createCATConstraints :execute? (get-in params [:execute?])) (createCATConstraints)) ))
    :available-media-types ["application/json"])


(defresource create-AT-Constraints [request]
    :service-available? true
    :allowed-methods [:post]    
    :handle-created (fn [_] (let [params (get-in request[:params])] (if (contains? params :execute?) (createATConstraints :execute? (get-in params [:execute?])) (createATConstraints)) ))
    :available-media-types ["application/json"])


(defresource create-CF-Constraints [request]
    :service-available? true
    :allowed-methods [:post]    
    :handle-created (fn [_] (let [params (get-in request[:params])] (if (contains? params :execute?) (createCFConstraints :execute? (get-in params [:execute?])) (createCFConstraints)) ))
    :available-media-types ["application/json"])


(defresource create-VRAT-Constraints [request]
    :service-available? true
    :allowed-methods [:post]    
    :handle-created (fn [_] (let [params (get-in request[:params])] (if (contains? params :execute?) (createVRATConstraints :execute? (get-in params [:execute?])) (createVRATConstraints)) ))
    :available-media-types ["application/json"])


(defresource create-CCAT-Constraints [request]
    :service-available? true
    :allowed-methods [:post]    
    :handle-created (fn [_] (let [params (get-in request[:params])] (if (contains? params :execute?) (createCCATConstraints :execute? (get-in params [:execute?])) (createCCATConstraints)) ))
    :available-media-types ["application/json"])


(defresource create-Class-Constraints [request]
    :service-available? true
    :allowed-methods [:post]    
    :handle-created (fn [_] (let [params (get-in request[:params])] (if (contains? params :execute?) (createClassConstraints :execute? (get-in params [:execute?])) (createClassConstraints)) ))
    :available-media-types ["application/json"])


(defresource create-All-Neo-Constraints [request]
    :service-available? true
    :allowed-methods [:post]    
    :handle-created (fn [_] (let [params (get-in request[:params])] (if (contains? params :execute?) (createAllNeoConstraints :execute? (get-in params [:execute?])) (createAllNeoConstraints)) ))
    :available-media-types ["application/json"])


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defresource create-Neo-Constraint [request]
    :service-available? true
    :allowed-methods [:post]    
    :handle-created (fn [_] (let [params (get-in request[:params])] (createNeoConstraint :constraintType (get-in params [:constraintType]) 
                                                                                         :constraintTarget (get-in params[:constraintTarget]) 
                                                                                         :execute? (if (contains? params :execute?) (get-in params [:execute?]) true) )))
    :available-media-types ["application/json"])


(defresource create-Attribute-Type [request]
    :service-available? true
    :allowed-methods [:post]    
    :handle-created (fn [_] (let [params (get-in request[:params])] (createAttributeType :_name (get-in params [:_name]) 
                                                                                         :_datatype (get-in params[:_datatype]) 
                                                                                         :subTypeOf (if (contains? params :subTypeOf) (get-in params [:subTypeOf]) [])
                                                                                         :subjectQualifier (if (contains? params :subjectQualifier) (get-in params [:subjectQualifier]) [])
                                                                                         :attributeQualifier (if (contains? params :attributeQualifier) (get-in params [:attributeQualifier]) [])
                                                                                         :valueQualifier (if (contains? params :valueQualifier) (get-in params [:valueQualifier]) [])
                                                                                         :execute? (if (contains? params :execute?) (get-in params [:execute?]) true) 
                                                                                         :subTypeOf (if (contains? params :subTypeOf) (get-in params [:subTypeOf]) []) )))
    :available-media-types ["application/json"])

(defresource create-Custom-Function [request]
    :service-available? true
    :allowed-methods [:post]    
    :handle-created (fn [_] (let [params (get-in request[:params])] (createCustomFunction :fnName (get-in params [:fnName]) 
                                                                                         :fnString (get-in params[:fnString]) 
                                                                                         :execute? (if (contains? params :execute?) (get-in params [:execute?]) true) )))
    :available-media-types ["application/json"])


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;








(defresource delete-Detach-Nodes [request]
    :service-available? true
    :allowed-methods [:delete]    

    :delete! (fn[_] (let [params (get-in request[:params])] (deleteDetachNodes :labels (get-in params [:labels]) 
                                                                               :parameters (if (contains? params :parameters) (stringify-keys (get-in params[:parameters])) {})  
                                                                               :execute? (if (contains? params :execute?) (get-in params [:execute?]) true) )))
    :available-media-types ["application/json"])

(defresource delete-Nodes [request]
    :service-available? true
    :allowed-methods [:delete]  
    
    :delete! (fn[_] (let [params (get-in request[:params])] (deleteNodes :labels (get-in params [:labels]) 
                                                                         :parameters (if (contains? params :parameters) (stringify-keys (get-in params[:parameters])) {})  
                                                                         :execute? (if (contains? params :execute?) (get-in params [:execute?]) true) )))
    :available-media-types ["application/json"])


















;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



(defresource get-Class-Attribute-Types [request]
  :service-available? true
  :allowed-methods [:get]
  :handle-ok (fn [_] (getClassAttributeTypes (get-in request [:params :className])))
  :available-media-types ["application/json"])

(defresource get-Class-Neo-Constraints [request]
  :service-available? true
  :allowed-methods [:get]
  :handle-ok (fn [_](getClassNeoConstraints (get-in request [:params :className])))
  :available-media-types ["application/json"])

(defresource get-Class-Custom-Constraints [request]
  :service-available? true
  :allowed-methods [:get]
  :handle-ok (fn [_] (getClassCustomConstraints (get-in request [:params :className])))
  :available-media-types ["application/json"])

(defresource get-AT-Value-Restrictions [request]
  :service-available? true
  :allowed-methods [:get]
  :handle-ok (fn [_] (getATValueRestrictions (get-in request [:params :atName])))
  :available-media-types ["application/json"])



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;




