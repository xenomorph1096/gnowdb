(ns gnowdb.handler
  (:require [compojure.core :refer [defroutes routes]]
            [ring.middleware.defaults :refer :all]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.file-info :refer [wrap-file-info]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.json :refer [wrap-json-params]]
            [ring.util.response :refer [response]]
            [ring.middleware.json :refer [wrap-json-response]]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [gnowdb.routes.gneo :refer [gneo-routes]]))

(defn init []
  (println "liberator-service is starting"))

(defn destroy []
  (println "liberator-service is shutting down"))

(defroutes app-routes
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (-> (routes gneo-routes app-routes)
      (handler/site)
      (wrap-json-params)
   ))
