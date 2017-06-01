(ns gnowdb.core
  (:gen-class)
  (:require [gnowdb.neo4j.gneo :as gneo]
  			[gnowdb.neo4j.gdriver :as gdriver]))

(use 'clojure.reflect)

(defn reload-all
  "Reload All"
  []
  (use 'gnowdb.core :reload-all))

(defn -main
  ""
  [& args])

(defn generateConf
	"Generates a default configuration file"
	[]
	(if (not (.exists (clojure.java.io/file "./src/gnowdb/neo4j/gconf.clj")))
		(spit "./src/gnowdb/neo4j/gconf.clj"
			{
			 :bolt-url "bolt://localhost:7687"
			 :username "neo4j"
			 :password "neo"
			}
		)
	)
)