(ns gnowdb.core
  (:gen-class)
  (:require [gnowdb.neo4j.gneo :as gneo]))

(use 'clojure.reflect)

(defn reload-all
  "Reload All"
  []
  (use 'gnowdb.core :reload-all))

(defn -main
  ""
  [& args])









