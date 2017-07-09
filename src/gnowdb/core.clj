(ns gnowdb.core
  (:gen-class)
  (:require [clojure.java.io :as io]
            [gnowdb.neo4j.gneo :as gneo]
            [gnowdb.neo4j.gdriver :as gdriver]
            [gnowdb.neo4j.gcust :as gcust]
            [gnowdb.neo4j.grcs :as grcs]
            [gnowdb.neo4j.grcs_locks :as grcs_locks]
            [gnowdb.neo4j.grcs_revert :as grcs_revert]
            [gnowdb.neo4j.dumprestore :as dumprestore]
            [gnowdb.spec.files :as files]
            [gnowdb.spec.init :as init]
            [gnowdb.neo4j.gqb :as gqb]
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
    (gdriver/getRCSEnabled details)
    (gneo/getUUIDEnabled details)
    (gcust/getCustomPassword details)
    (dumprestore/getBackupDirectory details)
    (files/getDataDirectory details)
    (grcs/getRCSConfig details)
    (files/getDataStorageLevels details)
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
                    :backup-directory "backups"
                    :data-directory "src/gnowdb/media"
                    :uuidEnabled true
                    :rcsEnabled true
                    :data-storage-levels 3
                    :rcs-directory "rcs-repo"
                    :rcs-bkp-dir "rcs-bkp"
                    :rcs-dir-levels 3
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
