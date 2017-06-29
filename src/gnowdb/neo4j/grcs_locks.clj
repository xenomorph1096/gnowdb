(ns gnowdb.neo4j.grcs_locks
  (:gen-class)
  (:require [gnowdb.neo4j.gneo :as gneo :only [getNBH]]
            [gnowdb.neo4j.grcs :as grcs :only [doRCS]]))

(def ^{:private true} var-prefix "VAR-")

(defn- concatVar
  [& {:keys [:UUID]}]
  {:pre [(string? UUID)]}
  (str var-prefix UUID))

(defn getVar
  [& {:keys [:UUID]}]
  (ns-resolve 'gnowdb.neo4j.grcs_locks (symbol (concatVar :UUID UUID))))

(defn- mlist?
  [v]
  (or (vector? v)
      (list? v)))

(defn- createVar
  [& {:keys [:UUID
             :value]}]
  (intern 'gnowdb.neo4j.grcs_locks (symbol (concatVar :UUID UUID)) value))

(def cvLock (atom nil))

(defn- initVars
  [& {:keys [:UUIDList]
      :or [:UUIDList []]}]
  {:pre [(mlist? UUIDList)]}
  (reset! cvLock
          (do (pmap #(let [vvar (getVar :UUID %)]
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
  {:pre [(mlist? UUIDList)]}
  (reset! fvLock
          (do (pmap #(let [vvar (getVar :UUID %)]
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
             :labels]
      :or {:UUIDList []
           :labels []}}]
  (initVars :UUIDList UUIDList)
  (let [nbhs (gneo/getNBH :UUIDList UUIDList
                          :labels labels)]
    (do (pmap (fn [nbhm]
                (let [uuid (nbhm 0)
                      nbh (nbhm 1)]
                  (swap! (var-get (getVar :UUID uuid))
                         (fn [at]
                           (grcs/doRCS :GDB_UUID uuid
                                       :newContent nbh)
                           (+ at (- 1 1))))))
              nbhs))
    (finalizeVars :UUIDList UUIDList)))
