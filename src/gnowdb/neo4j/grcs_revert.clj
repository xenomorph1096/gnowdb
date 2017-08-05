(ns gnowdb.neo4j.grcs_revert
  (:gen-class)
  (:require [gnowdb.neo4j.grcs :as grcs :only [rcsExists?
                                               rcs-sc-Exists?
                                               getLatest
                                               getLatest-sc
                                               co-p
                                               co-p-sc
                                               revList
                                               revList-sc
                                               restoreNode
                                               ]]
            [gnowdb.neo4j.gneo :as gneo :only [revertNode
                                               revertRelation
                                               deleteRelation
                                               mergeRelation
                                               reduceQueryColl
                                               createNewNode]]
            [gnowdb.neo4j.gdriver :as gdriver :only [getNBH
                                                     getSchema]]
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
             :date
             :latestRevision
             :getLatest?
             :getNBH?
             :execute?]
      :or {:execute? false
           :getLatest? false
           :rev ""
           :date ""
           :getNBH? false}}]
  {:pre [(or getLatest?
             getNBH?
             latestRevision)
         (grcs/rcsExists? :GDB_UUID UUID)
         ;; TODO : uncomment this line after a proper regex pattern for rcs revision numbers is established
         (or (contains? (grcs/revList :GDB_UUID UUID) rev)
             (not (nil? date)))
         ]}
  (let [oldRevision (read-string (grcs/co-p :GDB_UUID UUID
                                            :rev rev
                                            :date date))
        fr (if getNBH?
             ((gdriver/getNBH :UUIDList [UUID]) UUID)
             (if getLatest?
               (read-string (grcs/getLatest :GDB_UUID UUID))
               latestRevision))
        latestRevision (if (nil? fr)
                         {:node {:labels []
                                 :properties {}}
                          :inRelations #{}
                          :deleted? true}
                         fr)]
    (if (or (= latestRevision oldRevision)
            (and (latestRevision :deleted?)
                 (oldRevision :deleted?)))
      nil
      (if (and (oldRevision :deleted?)
               (not (latestRevision :deleted?)))
        (gneo/deleteDetachNodes :labels ((latestRevision :node) :labels)
                                :parameters ((latestRevision :node) :properties)
                                :execute? execute?)
        (if (and (latestRevision :deleted?)
                 (not (oldRevision :deleted?)))
          (let [nodeCreateQuery (gneo/createNewNode :uuid? false
                                                    :labels ((oldRevision :node) :labels)
                                                    :parameters ((oldRevision :node) :properties)
                                                    :execute? false)
                relQueries (map #(gneo/mergeRelation :fromNodeLabels []
                                                     :fromNodeParameters {"UUID" (% "fromUUID")}
                                                     :toNodeLabels []
                                                     :toNodeParameters {"UUID" (% "toUUID")}
                                                     :relationshipType ((% "relation") :labels)
                                                     :relationshipParameters ((% "relation") :properties)
                                                     :execute? false)
                                (oldRevision :inRelations))
                queries (concat [nodeCreateQuery]
                                relQueries)]
            (if execute?
              (apply gdriver/runQuery queries)
              queries))
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
              queriesList)))))))

(defn createConstraintQueries
  ""
  [CD constraints]
  (pmap #(-> {:query (str CD" "(% "description"))
              :parameters {}
              :schema-changed? true
              :override-nochange true})
        constraints))

(defn revertSchema
  "Revert neo4j schema to older revision"
  [& {:keys [:rev
             :date
             :getLatest?
             :getSchema?
             :execute?]
      :or {:getLatest? false
           :getSchema? false
           :execute? false
           :rev ""
           :date ""}}]
  {:pre [(or getLatest?
             getSchema?)
         (grcs/rcs-sc-Exists?)
         (or (contains? (grcs/revList-sc) rev)
             (not (= "" date)))]}
  (let [oldRevision (read-string (grcs/co-p-sc :rev rev
                                               :date date))
        latestRevision (if getLatest?
                         (read-string (grcs/getLatest-sc))
                         (gdriver/getSchema))
        oldConstraints (oldRevision :constraints)
        latestConstraints (latestRevision :constraints)
        remConstraints (createConstraintQueries "DROP"
                                                (clojure.set/difference latestConstraints
                                                                        oldConstraints))
        addConstraints (createConstraintQueries "CREATE"
                                                (clojure.set/difference oldConstraints
                                                                        latestConstraints))
        constraintQueries (concat remConstraints
                                  addConstraints)]
    (if execute?
      (apply gdriver/runQuery constraintQueries)
      {:remConstraints remConstraints
       :addConstraints addConstraints})))

(defn revertSubGraph
  [& {:keys [:UUIDList
             :date
             :getLatest?
             :getNBH?
             :execute?
             :revertSchema?]
      :or {:UUIDList []
           :date ""
           :getLatest? true
           :getNBH? false
           :revertSchema? false
           :execute? false}}]
  {:pre [(coll? UUIDList)
         (not (empty? UUIDList))
         (not (= "" date))
         (every? string? UUIDList)
         (or getNBH?
             getLatest?)]}
  (let [UUIDMap (apply merge (pmap #(-> {% 1}) UUIDList))
        uuidlset (into #{} UUIDList)
        UUIDPriorityMap (reduce (fn [umap
                                     uuid]
                                  (let [nbh (read-string (grcs/co-p :GDB_UUID uuid
                                                                    :date date))
                                        dependentUUIDs (filter #(contains? uuidlset %)
                                                               (map #(% "fromUUID")
                                                                    (nbh :inRelations)))]
                                    (reduce #(update %1
                                                     %2
                                                     (fn [a b]
                                                       (if (nil? a)
                                                         b
                                                         (+ a b)))
                                                     1)
                                            umap
                                            dependentUUIDs)))
                                UUIDMap
                                UUIDList)
        sortedUUIDList (sort #(> (UUIDPriorityMap %1)
                                 (UUIDPriorityMap %2)) UUIDList)
        dataQueries (gneo/reduceQueryColl (pmap #(revertNode :UUID %
                                                             :date date
                                                             :getLatest? getLatest?
                                                             :getNBH? getNBH?
                                                             :execute? false) sortedUUIDList))
        schemaQueries (if revertSchema?
                        (revertSchema :getLatest? getLatest?
                                      :getSchema? getNBH?
                                      :date date
                                      :execute? false)
                        {:remConstraints []
                         :addConstraints []})
        transactions [(schemaQueries :remConstraints)
                      dataQueries
                      (schemaQueries :addConstraints)]]
    (if execute?
      (apply gdriver/runTransactions transactions)
      transactions)))
