(ns gnowdb.neo4j.grcs_locks
  (:gen-class)
  (:require [gnowdb.neo4j.grcs :as grcs :only [doRCS]]))

(def ^{:private true} var-prefix "VAR-")

(defn- concatVar
  [& {:keys [:UUID]}]
  {:pre [(string? UUID)]}
  (str var-prefix UUID))

(defn getVar
  [& {:keys [:UUID]}]
  (ns-resolve 'gnowdb.neo4j.grcs_locks (symbol (concatVar :UUID UUID))))

(defn- createVar
  [& {:keys [:UUID
             :value]}]
  (intern 'gnowdb.neo4j.grcs_locks (symbol (concatVar :UUID UUID)) value))

(def cvLock (atom nil))

(defn- initVars
  [& {:keys [:UUIDList]
      :or [:UUIDList []]}]
  {:pre [(coll? UUIDList)]}
  (reset! cvLock
          (doall (pmap #(let [vvar (getVar :UUID %)]
                          (if (var? vvar)
                            (swap! (var-get vvar) + 1)
                            (createVar :UUID %
                                       :value (atom 1))))
                       UUIDList))))

(defn remVar
  [& {:keys [:UUID]}]
  (ns-unmap 'gnowdb.neo4j.grcs_locks (symbol (concatVar :UUID UUID))))

(def fvLock (atom nil))

(defn- finalizeVars
  [& {:keys [:UUIDList]
      :or [:UUIDList []]}]
  {:pre [(coll? UUIDList)]}
  (reset! fvLock
          (doall (pmap #(let [vvar (getVar :UUID %)]
                          (if (var? vvar)
                            (swap! (var-get vvar) (fn [cntr]
                                                    (let [cv (dec cntr)]
                                                      (if (= 0 cv)
                                                        (remVar :UUID %)
                                                        )
                                                      cv)))))
                       UUIDList))))

(defn queueUUIDs
  "Queues UUIDs for RCS"
  [& {:keys [:UUIDList
             :labels
             :nbhs]
      :or {:UUIDList []
           :labels []
           :nbhs {}}}]
  (let [init (initVars :UUIDList UUIDList)
        dS (doall (pmap (fn [nbhm]
                          (let [uuid (nbhm 0)
                                nbh (nbhm 1)]
                            (swap! (var-get (getVar :UUID uuid :init init))
                                   (fn [at]
                                     (grcs/doRCS :GDB_UUID uuid
                                                 :newContent nbh)
                                     (+ at (- 1 1))))))
                        nbhs))
        fv (finalizeVars :UUIDList UUIDList
                         :dS dS)]
    fv))
