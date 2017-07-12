(ns gnowdb.routes.files
  (:require [compojure.core :refer :all]
            [gnowdb.resources.files :refer :all]
            [cemerick.friend :as friend]
            (cemerick.friend [workflows :as workflows]
                             [credentials :as creds])
            [gnowdb.users :as users :refer (users)]
            [gnowdb.misc :as misc]
            [hiccup.page :as h]
            [hiccup.element :as e]
))

(def login-form
  [:div {:class "row"}
   [:div {:class "columns small-12"}
    [:h3 "Login"]
    [:div {:class "row"}
     [:form {:method "POST" :action "login" :class "columns small-4"}
      [:div "Username" [:input {:type "text" :name "username"}]]
      [:div "Password" [:input {:type "password" :name "password"}]]
      [:div [:input {:type "submit" :class "button" :value "Login"}]]]]]])




(defroutes files-routes

   (GET "/login" req
    (h/html5 misc/pretty-head (misc/pretty-body login-form)))

   (friend/logout (ANY "/logout" request (ring.util.response/redirect "/api")))

   (GET "/requires-authentication" req
    (friend/authenticated "Thanks for authenticating!"))
  (GET "/role-user" req
    (friend/authorize #{::users/user} "You're a user!"))
  (GET "/role-admin" req
    (friend/authorize #{::users/admin} "You're an admin!"))


  (context "/api" [] 

    (GET "/" request "<h1>WELCOME TO GNOWDB API<h1>")

    (GET "/getDataDirectory" request (friend/authorize #{::users/admin}                 
                                      (get-Data-Directory request)))

    (DELETE "/deleteFileFromGroupWorkspace" request (delete-File-From-Group-Workspace request))

    (DELETE "/deleteFileFromPersonalWorkspace" request (delete-File-From-Personal-Workspace request))

    (POST "/restoreFile" request (restore-File request))

    (DELETE "/purgeFile" request (purge-File request))

    (POST "/addFileToDB" request (add-File-To-DB request))

    (DELETE "/removeFileFromDB" request (remove-File-From-DB request))


    
    
  )
)