(ns gnowdb.routes.files
  (:require [compojure.core :refer :all]
            [gnowdb.resources.files :refer :all]))

(defroutes files-routes

  (context "/api" [] 

    (GET "/" request "<h1>WELCOME TO GNOWDB API<h1>")

    (GET "/getDataDirectory" request (str request))

    (DELETE "/deleteFileFromGroupWorkspace" request (delete-File-From-Group-Workspace request))

    (DELETE "/deleteFileFromPersonalWorkspace" request (delete-File-From-Personal-Workspace request))

    (POST "/restoreFile" request (restore-File request))

    (DELETE "/purgeFile" request (purge-File request))

    (POST "/addFileToDB" request (add-File-To-DB request))

    (DELETE "/removeFileFromDB" request (remove-File-From-DB request))
    
  )
)