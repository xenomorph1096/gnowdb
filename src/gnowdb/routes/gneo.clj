(ns gnowdb.routes.gneo
  (:require [compojure.core :refer :all]
            [gnowdb.resources.gneo :refer :all]))

;Includes all the resources to be exposed

(defroutes gneo-routes

  (context "/api" [] 

    (GET "/getAllNodes" request (get-All-Nodes request))
    (GET "/getNodes" request (get-Nodes request))
    (POST "/createNewNode" request (create-New-Node request))

    (GET "/getRelations" request (get-Relations request))
    (POST "/createRelation" request (create-Relation request))

    (GET "/getAllLabels" request (get-All-Labels request))
    (GET "/generateUUID" request (generate-UUID request))

    (POST "/createNCConstraints" request (create-NC-Constraints request))
    (POST "/createCATConstraints" request (create-CAT-Constraints request))
    (POST "/createATConstraints" request (create-AT-Constraints request))
    (POST "/createCFConstraints" request (create-CF-Constraints request))
    (POST "/createVRATConstraints" request (create-VRAT-Constraints request))
    (POST "/createCCATConstraints" request (create-CCAT-Constraints request))
    (POST "/createClassConstraints" request (create-Class-Constraints request))
    (POST "/createAllNeoConstraints" request (create-All-Neo-Constraints request))

    (POST "/createNeoConstraint" request (create-Neo-Constraint request))
    (POST "/createAttributeType" request (create-Attribute-Type request))
    (POST "/createCustomFunction" request (create-Custom-Function request))


    (DELETE "/deleteNodes" request (delete-Nodes request))

  )
)