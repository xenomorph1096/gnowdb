(ns gnowdb.neo4j.grcs
  (:gen-class)
  (:require [clojure.java.shell :as shell]
            [clojure.string :as clojure.string]
            [clojure.java.io :as clojure.java.io]
            [clojure.pprint :as clojure.pprint :only [pprint]]))

;;Based on https://github.com/keitax/wikirick2/
(def ^{:private true} neo4j-schema-filename "neo4j-schema")
(defn getRCSConfig
  [details]
  (def ^{:private true} rcsConfig {:rcs-directory (details :rcs-directory)
                                   :rcs-dir-levels (details :rcs-dir-levels)
                                   :rcs-bkp-dir (details :rcs-bkp-dir)
                                   }
    )
  (def ^{:private true} neo4j-schema-filepath (str (details :rcs-directory) "/" neo4j-schema-filename))
  )

(defn eg
  []
  neo4j-schema-filepath)

(defn isRevision?
  [str]
  (not (empty? (re-find #"^[1-9]+\.[1-9]+$" str))))

(defn- derivePath
  "Derives the folder path(e.g.1/2/3) where the node nbh file is to be stored inside the rcs directory"
  [& {:keys [:GDB_UUID
             :bkp?]
      :or {:bkp? false}}]
  {:pre [(string? GDB_UUID)]}
  (str (if bkp?
         (rcsConfig :rcs-bkp-dir)
         (rcsConfig :rcs-directory))
       "/"
       (clojure.string/join "/" (reverse (take-last (rcsConfig :rcs-dir-levels)
                                                    (clojure.string/split GDB_UUID #"")
                                                    )
                                         )
                            )
       "/"
       )
  )

(defn- deriveFilePath
  "Derives full path with file"
  [& {:keys [:GDB_UUID
             :bkp?]
      :or {:bkp? false}}]
  (str (derivePath :GDB_UUID GDB_UUID
                   :bkp? bkp?)
       GDB_UUID)
  )

(defn- spitFile
  "Spit into file"
  [& {:keys [:GDB_UUID
             :content]}]
  (shell/sh "mkdir" "-p" (derivePath :GDB_UUID GDB_UUID))
  (spit (deriveFilePath :GDB_UUID GDB_UUID) content))

(defn- slurpFile
  "Slurp  file"
  [& {:keys [:GDB_UUID]}]
  (slurp (deriveFilePath :GDB_UUID GDB_UUID)))

(defn- co-l
  "Check out with lock
  co -l filename"
  [& {:keys [:GDB_UUID]}]
  (shell/sh "co" "-l" GDB_UUID :dir (derivePath :GDB_UUID GDB_UUID))
  )

(defn- co-l-sc
  ""
  []
  (shell/sh "co" "-l" neo4j-schema-filename
            :dir (rcsConfig :rcs-directory)))

(defn- ci-u
  "Initial check-in of file (leaving file active in filesystem)
  ci -u filename"
  [& {:keys [:GDB_UUID
             :edit-comment]
      }
   ]
  {:pre [(string? GDB_UUID)
         (string? edit-comment)
         ]
   }
  (let [result (shell/sh "ci" "-u" GDB_UUID
                         :in edit-comment
                         :dir (derivePath :GDB_UUID GDB_UUID))]
    (if (= (:exit result) 0)
      (:out result)
      (throw (Exception. (:err result))
             )
      )
    )
  )

(defn- rcs-ci
  "Check-in file
  ci filename"
  [filename
   dir
   edit-comment]
  {:pre [(every? string? [filename
                          dir
                          edit-comment])]}
  (let [result (shell/sh "ci" filename
                         :in edit-comment
                         :dir dir)]
    (if (= (:exit result) 0)
      (:out result)
      (throw (Exception. (:err result))
             )
      )
    ))

(defn- ci
  [& {:keys [:GDB_UUID
             :edit-comment]
      :or {:edit-comment ""}
      }
   ]
  {:pre [(string? GDB_UUID)
         (string? edit-comment)
         ]
   }
  (rcs-ci GDB_UUID
          (derivePath :GDB_UUID GDB_UUID)
          edit-comment))

(defn- ci-sc
  [& {:keys [:edit-comment]
      :or {:edit-comment ""}}]
  (rcs-ci neo4j-schema-filename
          (rcsConfig :rcs-directory)
          edit-comment))

(defn- rcs-co-p
  "Display version x.y of a file
  co -px.y filename"
  [filename
   dir
   rev]
  {:pre [(every? string? [filename
                          dir])
         (or (= "" rev)
             (isRevision? rev))]}
  (let [args-r ["co"
                (str "-r"rev)
                "-p" filename
                :dir dir]
        result (apply shell/sh (if (= "" rev)
                                 (concat (subvec args-r 0 1) (subvec args-r 2))
                                 args-r))
        ]
    (if (= (:exit result) 0)
      (:out result)
      (throw (Exception. (:err result))))))

(defn co-p
  [& {:keys [:GDB_UUID
             :rev]
      :or {:rev ""}}]
  {:pre [(string? GDB_UUID)]
   }
  (rcs-co-p GDB_UUID
            (derivePath :GDB_UUID GDB_UUID)
            rev))

(defn co-p-sc
  [& {:keys [:rev]
      :or {:rev ""}}]
  (rcs-co-p neo4j-schema-filename
            (rcsConfig :rcs-directory)
            rev))

(defn rcsExists?
  "Returns whether rcs file exists for UUID"
  [& {:keys [:GDB_UUID
             :bkp?]
      :or {:bkp? false}}]
  (.exists (clojure.java.io/as-file (str (deriveFilePath :GDB_UUID GDB_UUID
                                                         :bkp? bkp?) ",v"))))

(defn rcs-sc-Exists?
  []
  (.exists (clojure.java.io/as-file (str neo4j-schema-filepath",v"))))

(defn getLatest
  "Get Latest Revision of a Node's NBH map"
  [& {:keys [:GDB_UUID]}]
  (co-p :GDB_UUID GDB_UUID))

(defn getLatest-sc
  []
  (rcs-co-p neo4j-schema-filename
            (rcsConfig :rcs-directory)
            ""))

(defn rcs-rlog
  [filename
   dir]
  (shell/sh "rlog" filename
            :dir dir))

(defn rlog
  "Get rlog for a UUID"
  [& {:keys [:GDB_UUID]}]
  (rcs-rlog GDB_UUID
            :dir (derivePath :GDB_UUID GDB_UUID)))

(defn rlog-sc
  []
  (rcs-rlog neo4j-schema-filename
            (rcsConfig :rcs-directory)))

(defn revList-rcs
  [rlg]
  (into #{} (distinct 
             (map #((clojure.string/split % #"revision ") 1)
                  (re-seq #"revision [1-9]+\.[1-9]+"
                          (rlg :out))))))

(defn revList
  "Get revision List for a UUID"
  [& {:keys [:GDB_UUID]}]
  (if (rcsExists? :GDB_UUID GDB_UUID)
    (revList-rcs (rlog :GDB_UUID GDB_UUID))))

(defn revList-sc
  "Get Revision List for neo4j-schema-file"
  []
  (if (rcs-sc-Exists?)
    (revList-rcs (rlog-sc))))

(defn backupNode
  "Move deleted node from :rcs-directory to :rcs-bkp-dir."
  [& {:keys [:GDB_UUID]}]
  (if (rcsExists? :GDB_UUID GDB_UUID)
    (do
      (shell/sh "mkdir" "-p" (derivePath :GDB_UUID GDB_UUID
                                         :bkp? true))
      (shell/sh "mv"
                (str (deriveFilePath :GDB_UUID GDB_UUID
                                     :bkp? false) ",v")
                (derivePath :GDB_UUID GDB_UUID :bkp? true)))
    nil))

(defn restoreNode
  "Move deleted node from :rcs-bkp-dir to :rcs-directory."
  [& {:keys [:GDB_UUID]}]
  (if (rcsExists? :GDB_UUID GDB_UUID
                  :bkp? true)
    (do
      (shell/sh "mkdir" "-p" (derivePath :GDB_UUID GDB_UUID
                                         :bkp? false))
      (shell/sh "mv"
                (str (deriveFilePath :GDB_UUID GDB_UUID
                                     :bkp? true) ",v")
                (derivePath :GDB_UUID GDB_UUID :bkp? false)))
    nil))

(defn doRCS
  "Perform RCS on a file using UUID
  :GDB_UUID should be a String UUID
  :newContent should be a map returned by gneo/getNBH.
  If newContent is nil, rcs nothing will happen"
  [& {:keys [:GDB_UUID
             :newContent]}]
  {:pre? [(string? GDB_UUID)
          (or (map? newContent)
              (nil? newContent))]}
  (if (not (nil? newContent))
    (if (rcsExists? :GDB_UUID GDB_UUID)
      (do
        (let [currContent (getLatest :GDB_UUID GDB_UUID)]
          (if (= newContent (read-string currContent))
            nil
            (do
              (co-l :GDB_UUID GDB_UUID)
              (spitFile :GDB_UUID GDB_UUID
                        :content (with-out-str (clojure.pprint/pprint newContent))
                        )
              (ci :GDB_UUID GDB_UUID)
              )
            )
          )
        )
      (do
        (spitFile :GDB_UUID GDB_UUID
                  :content (with-out-str (clojure.pprint/pprint newContent))
                  )
        (ci :GDB_UUID GDB_UUID)
        )
      )
    nil
    )
  )

(defn revisionSchema
  [newSchema]
  {:pre [(map? newSchema)]}
  (let [newContent (with-out-str (clojure.pprint/pprint newSchema))]
    (if (rcs-sc-Exists?)
      (do 
        (co-l-sc)
        (spit neo4j-schema-filepath newContent)
        (ci-sc))
      (do (spit neo4j-schema-filepath newContent)
          (ci-sc)))))
