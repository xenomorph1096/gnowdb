(ns gnowdb.neo4j.grcs
  (:gen-class)
  (:require [clojure.java.shell :as shell]
            [gnowdb.neo4j.gneo :as gneo]))

;;Based on https://github.com/keitax/wikirick2/

(def basedir "/home/user/temp/rcstemp/")



(defn co-l
  "Check out with lock
  co -l filename"
  [& {:keys [:title]}]
  (shell/sh "co" "-l" title :dir basedir))

(defn ci-u
  "Initial check-in of file (leaving file active in filesystem)
  ci -u filename"
  [& {:keys [:title
             :edit-comment]
      }
   ]
  {:pre [(string? title)
         (string? edit-comment)
         ]
   }
  (let [result (shell/sh "ci" "-u" title
                         :in edit-comment
                         :dir basedir)]
    (if (= (:exit result) 0)
      (:out result)
      (throw (Exception. (:err result))
             )
      )
    )
  )

(defn spitFile
  "Spit into file"
  [& {:keys [:title
             :text]}]
  (spit (str basedir title) text))

(defn ci
  "Check-in file
  ci filename"
  [& {:keys [:title
             :edit-comment]
      }
   ]
  {:pre [(string? title)
         (string? edit-comment)
         ]
   }
  (let [result (shell/sh "ci" title
                         :in edit-comment
                         :dir basedir)]
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
  [& {:keys [:title
             :rev]}]
  {:pre [(string? title)
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
                         "-p" title
                         :dir basedir)]
    (if (= (:exit result) 0)
      (:out result)
      (throw (Exception. (:err result))
             )
      )
    )
  )
