(ns gnowdb.neo4j.dumprestore
  (:gen-class)
  (:require [gnowdb.neo4j.gdriver :as gdriver]
  )
)

(defn dump
  [& {:keys [:filePath]}]
  (gdriver/runQuery {:query "CALL apoc.export.graphml.all({filepath},{params})" :parameters { "filepath"  filePath
                                              "params" {"readLabels" true
                                                        "storeNodeIds" true
                                                        }
                                              }
            }
  )
  (map #(spit (str filePath ".cypher") (str "CREATE " (% "description") "\n") :append true) (first ((gdriver/runQuery {:query "CALL db.constraints()" :parameters {}}) :results)))
  ; nil
)

(defn restore
  [& {:keys [:filePath]}]
  (gdriver/runQuery {:query "CALL apoc.import.graphml({filepath},{params})" :parameters {
                                              "filepath" filePath
                                              "params" {"readLabels" true
                                                        "storeNodeIds" true
                                                        }
                                            }                   
                    }
  )
  (apply gdriver/runQuery (map 
    (fn [query]
      {:query query :parameters {}}
    ) 
    (line-seq (clojure.java.io/reader (str filePath ".cypher")))
    )
  )
)