(ns gnowdb.handler
  (:require [compojure.core :refer [defroutes routes]]
            [ring.middleware.defaults :refer :all]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.file-info :refer [wrap-file-info]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.json :refer [wrap-json-params]]
            [ring.util.response :refer [response]]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.middleware.session :refer [wrap-session]]

            [compojure.handler :as handler]
            [compojure.route :as route]
            
            [gnowdb.routes.gneo :refer [gneo-routes]]
            [gnowdb.routes.workspaces :refer [workspaces-routes]]
            [gnowdb.routes.files :refer [files-routes]]
            [gnowdb.routes.login :refer [login-routes]]

            [gnowdb.users :as users :refer (users)]
            
            [cemerick.friend :as friend]
            (cemerick.friend [workflows :as workflows]
                             [credentials :as creds])


            ))

(defn init []
  (println "liberator-service is starting"))

(defn destroy []
  (println "liberator-service is shutting down"))

(defroutes app-routes
  (route/resources "/")
  (route/not-found "Not Found"))




(def app
  (-> (routes gneo-routes workspaces-routes files-routes login-routes app-routes)  
      
      (friend/authenticate {:credential-fn (partial creds/bcrypt-credential-fn @users)
                            :workflows [(workflows/interactive-form)]
                            :allow-anon? true
                            :login-uri "/login"
                            :default-landing-uri "/api"})  
      (wrap-keyword-params)
      (wrap-params)      
      (wrap-nested-params) 
      (wrap-json-params) 
      (wrap-session)    
      (handler/site)
   ))



