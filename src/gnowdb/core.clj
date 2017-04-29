(ns gnowdb.core
  (:gen-class)
  (:require [gnowdb.coreapi :as coreapi]
            [gnowdb.app :as app]))


(defn reload-all
  "Reload All"
  []
  (use 'gnowdb.core :reload-all))
(defn -main
  ""
  [& args])









