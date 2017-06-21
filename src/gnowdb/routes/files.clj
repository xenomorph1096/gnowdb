(ns gnowdb.routes.files
  (:require [compojure.core :refer :all]
            [gnowdb.resources.files :refer :all]))

(defroutes files-routes

  (context "/api" [] 

    (GET "/getDataDirectory" request (get-Data-Directory request))

    (DELETE "/deleteFileFromGroupWorkspace" request (delete-File-From-Group-Workspace request))

    (DELETE "/deleteFileFromPersonalWorkspace" request (delete-File-From-Personal-Workspace request))

    (POST "/restoreFile" request (restore-File request))

    (DELETE "/purgeFile" request (purge-File request))

    (POST "/addFileToDB" request (add-File-To-DB request))

    (DELETE "/removeFileFromDB" request (remove-File-From-DB request))
    
  )
)