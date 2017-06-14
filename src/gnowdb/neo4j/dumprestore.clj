(ns gnowdb.neo4j.dumprestore
  (:gen-class)
  (:require [gnowdb.neo4j.gdriver :as gdriver]
  )
)

(defn getBackupDirectory
  [details]
  (def ^{:private true} backupDir 
    (details :backup-directory)
  )
)

(defn dump
  [& {:keys [:filePath]}]
  (println (gdriver/runQuery {:query "CALL apoc.export.graphml.all({filepath},{params})" :parameters { "filepath"  (str backupDir "/" filePath)
                                              "params" {"readLabels" true
                                                        "storeNodeIds" true
                                                        }
                                              }
            }
  ))
  ;This takes db.constraints() and converts it into runnable cypher script!
  (map #(spit (str backupDir "/" filePath ".cypher") (if (clojure.string/includes? (% "description") "IS NODE KEY")
                                          (str "CREATE " (clojure.string/replace (% "description") #"\b([\w:()\s]+)ASSERT\s([\w:.\s]+) IS NODE KEY"  "$1ASSERT ($2) IS NODE KEY") "\n") 
                                          (str "CREATE " (% "description") "\n")
                                        ) 
          :append true) 
    (first ((gdriver/runQuery {:query "CALL db.constraints()" :parameters {}}) :results)))
)

(defn restore
  [& {:keys [:filePath]}]
  (gdriver/runQuery {:query "CALL apoc.import.graphml({filepath},{params})" :parameters {
                                              "filepath" (str backupDir "/" filePath)
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
    (line-seq (clojure.java.io/reader (str backupDir "/" filePath ".cypher")))
    )
  )
)