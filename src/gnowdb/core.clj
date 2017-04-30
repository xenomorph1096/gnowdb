(ns gnowdb.core
  (:gen-class)
  (:require [gnowdb.neo4j :as neo4j]))


(defn reload-all
  "Reload All"
  []
  (use 'gnowdb.core :reload-all))
(defn -main
  ""
  [& args])









