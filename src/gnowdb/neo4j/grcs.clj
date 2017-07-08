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

(defn isRevision?
  [str]
  (not (empty? (re-find #"^[1-9]+\.[1-9]+$" str))))

(defn- derivePath
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

(defn- deriveFilePath
  "Derives full path with file"
  [& {:keys [:GDB_UUID]}]
  (str (derivePath :GDB_UUID GDB_UUID)
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

(defn- ci
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
             :rev]
      :or {:rev ""}}]
  {:pre [(string? GDB_UUID)
         (or (= "" rev)
             (isRevision? rev))
         ]
   }
  (let [args-r ["co"
                (str "-r"rev)
                "-p" GDB_UUID
                :dir (derivePath :GDB_UUID GDB_UUID)]
        result (apply shell/sh (if (= "" rev)
                                 (concat (subvec args-r 0 1) (subvec args-r 2))
                                 args-r))
        ]
    (if (= (:exit result) 0)
      (:out result)
      (throw (Exception. (:err result))
             )
      )
    )
  )

(defn rcsExists?
  "Returns whether rcs file exists for UUID"
  [& {:keys [:GDB_UUID]}]
  (.exists (clojure.java.io/as-file (str (deriveFilePath :GDB_UUID GDB_UUID) ",v"))))

(defn getLatest
  "Get Latest Revision of a Node's NBH map"
  [& {:keys [:GDB_UUID]}]
  (co-p :GDB_UUID GDB_UUID))

(defn rlog
  "Get rlog for a UUID"
  [& {:keys [:GDB_UUID]}]
  (shell/sh "rlog" GDB_UUID
            :dir (derivePath :GDB_UUID GDB_UUID)))

(defn revList
  "Get revision List for a UUID"
  [& {:keys [:GDB_UUID]}]
  (into #{} (distinct (if (rcsExists? :GDB_UUID GDB_UUID)
                        (map #((clojure.string/split % #"revision ") 1)
                             (re-seq #"revision [1-9]+\.[1-9]+"
                                     ((rlog :GDB_UUID GDB_UUID) :out)))
                        '()))))

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
    nil
    )
  )
