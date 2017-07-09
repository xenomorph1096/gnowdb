(ns gnowdb.neo4j.grcs_revert
  (:gen-class)
  (:require [gnowdb.neo4j.grcs :as grcs :only [rcsExists?
                                               getLatest
                                               co-p
                                               revList]]
            [gnowdb.neo4j.gneo :as gneo :only [revertNode
                                               revertRelation
                                               deleteRelation
                                               mergeRelation]]
            [gnowdb.neo4j.gdriver :as gdriver :only [getNBH]]
            [gnowdb.neo4j.gqb :as gqb]))

(defn revertNode
  "Revert a node to an older revision.
  :UUID should be UUID of the node
  :rev should be a string, rcs revision number
  :latestRevision should be the NBH of the node in question, as returned by gdriver/getNBH, or optionally fetched using grcs/getLatest.
  :getNBH? to be used if the latestRevision is to be fetched using getNBH.
  :getLatest? to be used if latestRevision is to be fetched using latest rcs revision of node"
  [& {:keys [:UUID
             :rev
             :latestRevision
             :getLatest?
             :getNBH?
             :execute?]
      :or {:execute? false
           :getLatest? false
           :getNBH? false}}]
  {:pre [(or getLatest?
             getNBH?
             latestRevision)
         (grcs/rcsExists? :GDB_UUID UUID)
         ;; TODO : uncomment this line after a proper regex pattern for rcs revision numbers is established
         (contains? (grcs/revList :GDB_UUID UUID) rev)
         ]}
  (let [oldRevision (read-string (grcs/co-p :GDB_UUID UUID
                                            :rev rev))
        latestRevision (if getNBH?
                         ((gdriver/getNBH :UUIDList [UUID]) UUID)
                         (if getLatest?
                           (read-string (grcs/getLatest :GDB_UUID UUID))
                           latestRevision))]
    (if (= latestRevision oldRevision)
      nil
      (let [relQueries (reduce #(let [filRel (first (filter (fn [lIR]
                                                              (and (= ((%2 "relation") :labels)
                                                                      ((lIR "relation") :labels))
                                                                   (not (contains? (%1 :relToRev)
                                                                                   lIR))))
                                                            (latestRevision :inRelations)))]
                                  (if (nil? filRel)
                                    (update (update %1 :relToAdd
                                                    (fn [or]
                                                      (conj or filRel))) :relAddQ
                                            (fn [or]
                                              (conj or (gneo/mergeRelation :fromNodeLabels []
                                                                           :fromNodeParameters {"UUID" (%2 "fromUUID")}
                                                                           :toNodeLabels []
                                                                           :toNodeParameters {"UUID" (%2 "toUUID")}
                                                                           :relationshipType ((%2 "relation") :labels)
                                                                           :relationshipParameters ((%2 "relation") :properties)
                                                                           :execute? false))))
                                    (update (update %1 :relToRev
                                                    (fn [or]
                                                      (conj or filRel))) :relRevQ
                                            (fn [or]
                                              (conj or (gneo/revertRelation :fromNodeLabels []
                                                                            :fromNodeParameters {"UUID" (%2 "fromUUID")}
                                                                            :toNodeLabels []
                                                                            :toNodeParameters {"UUID" (%2 "toUUID")}
                                                                            :relationshipType ((%2 "relation") :labels)
                                                                            :matchRelationshipParameters ((filRel "relation") :properties)
                                                                            :newRelationshipParameters ((%2 "relation") :properties)
                                                                            :execute? false))))))
                               {:relToRev []
                                :relToAdd []
                                :relRevQ []
                                :relAddQ []}
                               (oldRevision :inRelations))
            relDelQ (reduce #(if (contains? (into #{} (relQueries :relToRev)) %2)
                               %1
                               (conj %1 (gneo/deleteRelation :fromNodeLabels []
                                                             :toNodeLabels []
                                                             :fromNodeParameters {"UUID" (%2 "fromUUID")}
                                                             :toNodeParameters {"UUID" (%2 "toUUID")}
                                                             :relationshipParameters ((%2 "relation") :properties)
                                                             :relationshipType ((%2 "relation") :labels)
                                                             :execute? false)))
                            []
                            (latestRevision :inRelations))
            queriesList (concat relDelQ
                                (relQueries :relRevQ)
                                (relQueries :relAddQ)
                                [(gneo/revertNode :matchLabels ((latestRevision :node) :labels)
                                                  :newLabels ((oldRevision :node) :labels)
                                                  :matchProperties ((latestRevision :node) :properties)
                                                  :newProperties ((oldRevision :node) :properties)
                                                  :execute? false)])]
        (if execute?
          (apply gdriver/runQuery queriesList)
          queriesList)))))

(defn restoreNode
  "Restore a node that is deleted from neo4j"
  [& {:keys [:UUID
             :rev]}])
