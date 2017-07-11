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
            [compojure.handler :as handler]
            [compojure.route :as route]
            [gnowdb.routes.gneo :refer [gneo-routes]]
            [gnowdb.routes.workspaces :refer [workspaces-routes]]
            [gnowdb.routes.files :refer [files-routes]]
            [cemerick.friend.credentials :as creds]
            [gnowdb.authentication.middleware.auth :as auth]
            [gnowdb.authentication.resources :as r :refer :all]))

(defn init []
  (println "liberator-service is starting"))

(defn destroy []
  (println "liberator-service is shutting down"))

(defroutes app-routes
  (route/resources "/")
  (route/not-found "Not Found"))




(def users
  "dummy in-memory user database."
  
  {
    "a" {   :username "a"
            :password (creds/hash-bcrypt "abc")
            :roles #{:admin}
        }
   
    "d" {   :username "d"
            :password (creds/hash-bcrypt "def")
            :roles #{:user}
        }
  }
)


(def app
  (-> (routes gneo-routes workspaces-routes files-routes app-routes)
      (handler/site)
      (wrap-json-params)
      (wrap-params)
      (wrap-keyword-params)
      (wrap-nested-params)
      (auth/friend-middleware users)
   ))




