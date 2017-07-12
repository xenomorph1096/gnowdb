(ns gnowdb.routes.files
  (:require [compojure.core :refer :all]
            [gnowdb.resources.files :refer :all]
            [cemerick.friend :as friend]
            (cemerick.friend [workflows :as workflows]
                             [credentials :as creds])
            [gnowdb.users :as users :refer (users)]            
  )
)


(defroutes files-routes

   (context "/api" [] 

    (GET "/getDataDirectory" request (friend/authorize #{::users/user} 
                                      (get-Data-Directory request)))

    (DELETE "/deleteFileFromGroupWorkspace" request (friend/authorize #{::users/admin} 
                                                      (delete-File-From-Group-Workspace request)))

    (DELETE "/deleteFileFromPersonalWorkspace" request (friend/authorize #{::users/admin} 
                                                        (delete-File-From-Personal-Workspace request)))

    (POST "/restoreFile" request (friend/authorize #{::users/admin}
                                  (restore-File request)))

    (DELETE "/purgeFile" request (friend/authorize #{::users/admin}
                                    (purge-File request)))

    (POST "/addFileToDB" request (friend/authorize #{::users/admin}
                                  (add-File-To-DB request)))

    (DELETE "/removeFileFromDB" request (friend/authorize #{::users/admin}
                                          (remove-File-From-DB request)))   
    
  )
)