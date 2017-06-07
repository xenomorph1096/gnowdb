(ns gnowdb.core
  (:gen-class)
  (:require [clojure.java.io :as io]
  			[gnowdb.neo4j.gneo :as gneo]
            [gnowdb.neo4j.gdriver :as gdriver]
            [gnowdb.neo4j.gcust :as gcust]
            [gnowdb.spec.init :as init]
            [async-watch.core :refer [changes-in cancel-changes]]))

(import '[java.io PushbackReader])

(defn reload-all
  "Reload All"
  []
  (use 'gnowdb.core :reload-all))

(defn -main
  ""
  [& args])

(defn- initiateReadback
	"Redifines global variables in other namespaces where needed"
	[]
	(let [details 
			(with-open [r (io/reader "src/gnowdb/neo4j/gconf.clj")]
							(read (PushbackReader. r))
			)
		 ]
		 ;Add readbackfunctions here with the desired data
		(gdriver/getNeo4jDBDetails details)
		(gcust/getCustomPassword details)
	)
)


(defn- generateConf
  "Generates a default configuration file"
  	[]
  	(let [defaultMap {
                 :bolt-url "bolt://localhost:7687"
                 :username "neo4j"
                 :password "neo"
                 :customFunctionPassword "password"
                 }
        ]
	  	(if (not (.exists (clojure.java.io/file "src/gnowdb/neo4j/gconf.clj")))
	    	(spit "src/gnowdb/neo4j/gconf.clj"
	          	defaultMap
	        )
	    	(let [existingMap 
	    			(with-open [r (io/reader "src/gnowdb/neo4j/gconf.clj")]
									(read (PushbackReader. r))
					)
				]
				(spit "src/gnowdb/neo4j/gconf.clj"
	          		(merge defaultMap existingMap)
	        	)
	    	)
	    )
    )
 )

(generateConf)
(initiateReadback)
(init/init)

(let [changes (changes-in ["src/gnowdb/neo4j"])]
	(clojure.core.async/go 
		(while true
			(let [[op filename] (<! changes)]
				;; op will be one of :create, :modify or :delete
				(if (= filename "src/gnowdb/neo4j/gconf.clj")
					(if (= op :delete)
						(cancel-changes)
						(initiateReadback)
					)
				)
			)
		)
	)
)
