(ns gnowdb.routes.login
  (:require [compojure.core :refer :all]
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


(defroutes login-routes

  (GET "/api" req "WELCOME TO GNOWDB API")

   (GET "/login" req
    (h/html5 misc/pretty-head (misc/pretty-body login-form)))

   (friend/logout (ANY "/logout" request (ring.util.response/redirect "/api")))

   (GET "/requires-authentication" req
    (friend/authenticated "Thanks for authenticating!"))
   (GET "/role-user" req
    (friend/authorize #{::users/user} "You're a user!"))
   (GET "/role-admin" req
    (friend/authorize #{::users/admin} "You're an admin!"))
)