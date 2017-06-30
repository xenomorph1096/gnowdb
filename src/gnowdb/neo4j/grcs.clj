(ns gnowdb.neo4j.grcs
  (:gen-class)
  (:require [clojure.java.shell :as shell]
            [clojure.string :as clojure.string]
            [clojure.java.io :as clojure.java.io]))

;;Based on https://github.com/keitax/wikirick2/

(defn getRCSConfig
  [details]
  (def ^{:private true} rcsConfig {:rcs-directory (details :rcs-directory)
                                   :rcs-dir-levels (details :rcs-dir-levels)
                                   }
    )
  )

(def UUID "47abd454-61b7-4eb4-a89b-046711ff20ec")

(defn derivePath
  "Derives the folder path(e.g.1/2/3) where the node nbh file is to be stored inside the rcs directory"
  [& {:keys [:GDB_UUID]}]
  {:pre [(string? GDB_UUID)]}
  (str (rcsConfig :rcs-directory)
       "/"
       (clojure.string/join "/" (reverse (take-last (rcsConfig :rcs-dir-levels)
                                                    (clojure.string/split GDB_UUID #"")
                                                    )
                                         )
                            )
       "/"
       )
  )

(defn deriveFilePath
  "Derives full path with file"
  [& {:keys [:GDB_UUID]}]
  (str (derivePath :GDB_UUID GDB_UUID)
       GDB_UUID)
  )

(defn spitFile
  "Spit into file"
  [& {:keys [:GDB_UUID
             :content]}]
  (shell/sh "mkdir" "-p" (derivePath :GDB_UUID GDB_UUID))
  (spit (deriveFilePath :GDB_UUID GDB_UUID) content))

(defn slurpFile
  "Slurp  file"
  [& {:keys [:GDB_UUID]}]
  (slurp (deriveFilePath :GDB_UUID GDB_UUID)))

(defn co-l
  "Check out with lock
  co -l filename"
  [& {:keys [:GDB_UUID]}]
  (shell/sh "co" "-l" GDB_UUID :dir (derivePath :GDB_UUID GDB_UUID))
  )

(defn ci-u
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

(defn ci
  "Check-in file
  ci filename"
  [& {:keys [:GDB_UUID
             :edit-comment]
      :or {:edit-comment ""}
      }
   ]
  {:pre [(string? GDB_UUID)
         (string? edit-comment)
         ]
   }
  (let [result (shell/sh "ci" GDB_UUID
                         :in edit-comment
                         :dir (derivePath :GDB_UUID GDB_UUID))]
    (if (= (:exit result) 0)
      (:out result)
      (throw (Exception. (:err result))
             )
      )
    )
  )

(defn co-p
  "Display version x.y of a file
  co -px.y filename"
  [& {:keys [:GDB_UUID
             :rev]}]
  {:pre [(string? GDB_UUID)
         (map? rev)
         (and (contains? rev :x)
              (contains? rev :y)
              )
         ]
   }
  (let [result (shell/sh "co"
                         (format "-r%s.%s"
                                 (rev :x)
                                 (rev :y)
                                 )
                         "-p" GDB_UUID
                         :dir (derivePath :GDB_UUID GDB_UUID))]
    (if (= (:exit result) 0)
      (:out result)
      (throw (Exception. (:err result))
             )
      )
    )
  )

(defn rlog
  "Get rlog for a UUID"
  [& {:keys [:GDB_UUID]}]
  (shell/sh "rlog" GDB_UUID
            :dir (derivePath :GDB_UUID GDB_UUID)))

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
    (let [filePath (deriveFilePath :GDB_UUID GDB_UUID)]
      (if (.exists (clojure.java.io/as-file (str filePath ",v")))
        (do
          (co-l :GDB_UUID GDB_UUID)
          (let [currContent (slurpFile :GDB_UUID GDB_UUID)]
            (if (= newContent (read-string currContent))
              (ci :GDB_UUID GDB_UUID)
              (do
                (spitFile :GDB_UUID GDB_UUID
                          :content (pr-str newContent)
                          )
                (ci :GDB_UUID GDB_UUID)
                )
              )
            )
          )
        (do
          (spitFile :GDB_UUID GDB_UUID
                    :content (pr-str newContent)
                    )
          (ci :GDB_UUID GDB_UUID)
          )
        )
      )
    nil
    )
  )















