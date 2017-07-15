(ns gnowdb.neo4j.gdriver
  (:gen-class)
  (:require [clojure.set :as clojure.set]
            [clojure.java.io :as io]
            [clojure.string :as clojure.string]
            [gnowdb.neo4j.grcs :as grcs :only [backupNode
                                               revisionSchema]]
            [gnowdb.neo4j.grcs_locks :as grcs_locks :only [queueUUIDs]])
  (:use [gnowdb.neo4j.gqb]))

(import '[org.neo4j.driver.v1 Driver AuthTokens GraphDatabase Record Session StatementResult Transaction Values])

(declare getNBH
         getSchema)

(defn getNeo4jDBDetails
  [details]
  (if (bound? (resolve `driver))
    (.close (var-get (resolve `driver)))
    )
  (def ^{:private true} driver 
    (GraphDatabase/driver (details :bolt-url) (AuthTokens/basic (details :username) (details :password)))
    )
  )

(defn getRCSEnabled
  [details]
  (def ^{:private true} rcsEnabled? (details :rcsEnabled)))

(defn- createSummaryMap
  "Creates a summary map from StatementResult object.
	This Object is returned by the run() method of session object
	To be used for cypher queries that dont return nodes
	Driver should not be closed before invoking this function"
  [statementResult]
  (let [summaryCounters (.counters (.consume statementResult))]
    {:constraintsAdded (.constraintsAdded summaryCounters) :constraintsRemoved (.constraintsRemoved summaryCounters) :containsUpdates (.containsUpdates summaryCounters) :indexesAdded (.indexesAdded summaryCounters) :indexesRemoved (.indexesRemoved summaryCounters) :labelsAdded (.labelsAdded summaryCounters) :labelsRemoved (.labelsRemoved summaryCounters) :nodesCreated (.nodesCreated summaryCounters) :nodesDeleted (.nodesDeleted summaryCounters) :propertiesSet (.propertiesSet summaryCounters) :relationshipsCreated (.relationshipsCreated summaryCounters) :relationshipsDeleted (.relationshipsDeleted summaryCounters)}))

(defn- createSummaryString 
  "Creates Summary String only with only necessary information.
  	Takes summaryMap created by createSummaryMap function."
  [summaryMap]
  (reduce
   (fn [string k]
     (if(not= (k 1) 0)
       (str string (str (clojure.string/capitalize (subs (str (k 0)) 1 2)) (subs (str (k 0)) 2)) " :" (k 1) " ;")
       string
       )
     )
   ""
   summaryMap
   )
  )

(defn- getFullSummary
  "Returns summaryMap and summaryString"
  [statementResult]
  (let [sumMap (createSummaryMap statementResult)]
    {:summaryMap sumMap :summaryString (createSummaryString sumMap)}
    )
  )

(defn- getCombinedFullSummary
  "Combine FullSummaries obtained from 'getFullSummary'"
  [fullSummaryVec]
  (let [ consolidatedMap 
        (apply merge-with
               (fn [& args]
                 (if (some #(= (type %) java.lang.Boolean) args)
                   (if (some identity args) true false)
                   (apply + args)
                   )
                 )
               {:constraintsAdded 0 :constraintsRemoved 0 :containsUpdates false :indexesAdded 0 :indexesRemoved 0 :labelsAdded 0 :labelsRemoved 0 :nodesCreated 0 :nodesDeleted 0 :propertiesSet 0 :relationshipsCreated 0 :relationshipsDeleted 0}	
               (map #(% :summaryMap) fullSummaryVec)
               )
        ]
    {:summaryMap consolidatedMap
     :summaryString (createSummaryString consolidatedMap)
     }
    )
  )

(defn- parse
  [data]
  (cond ;More parsers can be added here. (instance? /*InterfaceName*/ data) <Return Value>
    (instance? org.neo4j.driver.v1.types.Node data) {:labels (into [] (.labels data)) :properties (into {} (.asMap data))}
    (instance? org.neo4j.driver.v1.types.Relationship data) {:labels (.type data) :properties (into {} (.asMap data)) :fromNode (.startNodeId data) :toNode (.endNodeId data)}
    (instance? org.neo4j.driver.v1.types.Path data) {:start (parse (.start data)):end (parse (.end data)) :segments (map (fn [segment] {:start (parse (.start segment)) :end (parse (.end segment)) :relationship (parse (.relationship segment))}) data) :length (reduce (fn [counter, data] (+ counter 1)) 0 data)}
    :else data
    )
  )

(defn getRCSUUIDListMap
  "Create a map with keys :count,:RCSUUIDList, using `finalResult` of a transaction from gdriver/runQuery.
  :RCSUUIDList will be a vector of maps with keys :UUIDList, :labels.
  :queriesList should be the query maps that are passed to gdriver/runQuery"
  [& {:keys [:finalResult
             :queriesList]
      :or {:finalResult {:results []}
           :queriesList []}}]
  {:pre [(= (count (finalResult :results))
            (count queriesList))]}
  {:RCSUUIDList (filter identity
                        (map (fn [query
                                  result]
                               (if (= true
                                      (query :doRCS?))
                                 {:UUIDList (reduce concat (pmap #(map (fn [rvar]
                                                                         (% rvar))
                                                                       (query :rcs-vars))
                                                                 result))
                                  :labels (query :labels)
                                  :doRCS? true}))
                             queriesList
                             (finalResult :results)))
   :RCSBkpList (filter identity
                       (flatten (map (fn [query
                                          result]
                                       (if (query :rcs-bkp?)
                                         (pmap #(map (fn [rvar]
                                                       (% rvar))
                                                     (query :rcs-vars))
                                               result)))
                                     queriesList
                                     (finalResult :results))))})

(defn reduceRCSUUIDListMap
  "Groups UUIDs based on labels.
  if a UUIDList's labels are a superset of another UUIDList, it would be 'unioned' to the latter
  :RCSUUIDListMap should be output of gdriver/getRCSUUIDListMap"
  [& {:keys [:RCSUUIDListMap]
      :or {:RCSUUIDListMap {:count 0
                            :RCSUUIDList []}}}]
  (let [RCSUUIDList (sort #(> (%1 :UUIDCount)
                              (%2 :UUIDCount))
                          (pmap (fn [UL]
                                  (assoc UL
                                         :UUIDList (into #{} (ddistinct (UL :UUIDList)))
                                         :labels (into #{} (ddistinct (UL :labels)))
                                         :UUIDCount (count (UL :UUIDList))))
                                (RCSUUIDListMap :RCSUUIDList)))
        reducedRCSUUIDList (reduce (fn
                                     [RUL UL]
                                     (let [candidateULs (reduce (fn [CULs cul]
                                                                  (if (and (>= (cul :UUIDCount)
                                                                               (UL :UUIDCount))
                                                                           (clojure.set/subset? (cul :labels)
                                                                                                (UL :labels)))
                                                                    (conj CULs {:index (.indexOf RUL cul)
                                                                                :UUIDCount (cul :UUIDCount)})
                                                                    CULs))
                                                                []
                                                                RUL)
                                           largestCUL (if (empty? candidateULs)
                                                        (apply max-key [:UUIDCount nil])
                                                        (apply max-key :UUIDCount candidateULs))]
                                       (if (nil? largestCUL)
                                         (conj RUL UL)
                                         (let [ri (largestCUL :index)
                                               lu (RUL ri)
                                               union (clojure.set/union (UL :UUIDList)
                                                                        (lu :UUIDList))]
                                           (assoc RUL
                                                  ri (assoc lu
                                                            :UUIDList union
                                                            :UUIDCount (count union)))))))
                                   []
                                   RCSUUIDList)]
    (assoc RCSUUIDListMap :RCSUUIDList reducedRCSUUIDList)))

(defn- doRCS
  [& {:keys [:finalResult
             :queriesList
             :tx]}]
  (let [ruuidlistm (getRCSUUIDListMap :finalResult finalResult
                                      :queriesList queriesList)
        RCSUUIDListMap (reduceRCSUUIDListMap :RCSUUIDListMap ruuidlistm)]
    (doall (map (fn [umap]
                  (if (umap :doRCS?)
                    (grcs_locks/queueUUIDs :UUIDList (umap :UUIDList)
                                           :nbhs (getNBH :labels (umap :labels)
                                                         :UUIDList (umap :UUIDList)
                                                         :tx tx)
                                           :labels (umap :labels))))
                (RCSUUIDListMap :RCSUUIDList)))
    (doall (pmap #(grcs/backupNode :GDB_UUID %) (ruuidlistm :RCSBkpList)))))

(defn pList
  [stList]
  (map 
   (fn [record]
     (into {} 
           (map 
            (fn 
              [attribute]
              {(attribute 0) (parse (attribute 1))}
              )
            (into {} (.asMap record))
            )
           )
     ) 
   stList
   ))

(defn runQuery
  "Takes a list of queries and executes them. Returns a map with all records and summary of operations iff all operations are successful otherwise fails.
	Input Format: {:query <query> :parameters <Map of parameters as string key-value pairs>} ...
	Output Format: {:results [(result 1) (result 2) .....] :summary <Summary Map>}
	In case of failure, {:results [] :summary <default full summary>}"
  [& queriesList]
  (let [session (.session driver)
        transaction (.beginTransaction session)
        ]
    (try
      (let
          [finalResult (reduce
                        (fn [resultMap queryMap]
                          (let [statementResult (.run transaction (queryMap :query) (java.util.HashMap. (queryMap :parameters)))]
                            {:results (conj 
                                       (resultMap :results) 
                                       (pList (.list statementResult))) 
                             :summary (getCombinedFullSummary [(resultMap :summary) (getFullSummary statementResult)])
                             }
                            )
                          )
                        {:results [] :summary (getCombinedFullSummary [])}
                        queriesList
                        )
           schemaQueries (filter #(= true (% :schema-changed?)) queriesList)]
        (if
            (and (((finalResult :summary) :summaryMap) :containsUpdates)
                 rcsEnabled?)
          (doRCS :finalResult finalResult
                 :queriesList queriesList
                 :tx transaction))
        (if (and rcsEnabled?
                 (not (empty? schemaQueries))
                 (or (((finalResult :summary) :summaryMap) :containsUpdates)
                     (not (empty? (filter #(= true (% :override-nochange))
                                          schemaQueries)))))
          (grcs/revisionSchema (getSchema :tx transaction)))
        (.success transaction)
        finalResult
        )
      (catch Throwable e (.failure transaction) {:results [] :summary {:summaryMap {} :summaryString (.toString e)}})
      (finally (.close transaction) (.close session))
      )
    )
  )

(defn runTransactions
  "Takes lists of arguments to run in separate transactions"
  [& transactionList]
  (let
      [result (map
               #(apply runQuery %)
               transactionList
               )]
    {:results result
     :summary (getCombinedFullSummary (map #(% :summary) result))
     }
    )
  )

(defn- sQ
  [tx query params]
  (pList (.list (.run tx query params))))

(defn getNodesByUUID
  "Get Nodes by UUID"
  [& {:keys [:labels
             :UUIDList
             :tx]
      :or {:labels []
           :tx nil}}]
  {:pre [(coll? labels)
         (coll? UUIDList)
         (every? string? UUIDList)]}
  (let [builtQuery {:query (str "MATCH (node"(createLabelString :labels labels)") WHERE node.UUID in {UUIDList} return node")
                    :parameters {"UUIDList" UUIDList}}]
    (reduce #(merge %1
                    {((%2 :properties) "UUID") %2})
            {} (map #(% "node")
                    (if (nil? tx)
                      (first ((runQuery builtQuery) :results))
                      (sQ tx (builtQuery :query) (builtQuery :parameters)))))
    )
  )

(defn getInRels
  [& {:keys [:labels
             :UUIDList
             :tx]
      :or {:labels []
           :UUIDList []
           :tx nil}}]
  {:pre [(every? string? UUIDList)]}
  (let [labelString (createLabelString :labels labels)
        builtQuery {:query (str "MATCH (n"
                                labelString
                                " )<-[relation]-(node)"
                                " WHERE n.UUID IN {UUIDList}"
                                " RETURN relation, node.UUID as fromUUID, n.UUID as toUUID")
                    :parameters {"UUIDList" UUIDList}}
        inRels (if (nil? tx)
                 (first ((runQuery builtQuery) :results))
                 (sQ tx (builtQuery :query) (builtQuery :parameters)))]
    (reduce #(assoc %1 %2
                    (into #{} (filter
                               (fn [rel]
                                 (= %2 (rel "toUUID"))) (map (fn [rel]
                                                               (assoc rel "relation"(dissoc (rel "relation") :fromNode :toNode))) inRels)))) {} UUIDList)))

(def nbhAtom (atom nil)) 

(defn getNBH
  "GET NBH"
  [& {:keys [:labels
             :UUIDList
             :tx]
      :or {:labels []
           :UUIDList []
           :tx nil}}]
  {:pre [(coll? UUIDList)
         (every? string? UUIDList)]}
  (reset! nbhAtom
          (let [nodesMatched (getNodesByUUID :UUIDList UUIDList
                                             :tx tx)
                nodeNBHs (getInRels :labels labels
                                    :UUIDList UUIDList
                                    :tx tx)
                ]
            (reduce #(merge %1 {(%2 0) {:node (assoc (%2 1) :labels (into #{} ((%2 1) :labels)))
                                        :inRelations (nodeNBHs (%2 0))}})
                    {} nodesMatched))))

(defn fixConstraintQueries
  [constraints]
  (map #(let [splitS (clojure.string/split (% "description") #" ")
              str (first (take-last 4 splitS))]
          (if (and (= ["IS" "NODE" "KEY"]
                      (take-last 3 splitS))
                   (not (clojure.string/includes? str ")")))
            (assoc % "description"
                   (clojure.string/join " "
                                        (concat (take (- (count splitS) 4)
                                                      splitS)
                                                ["(" str ")"]
                                                (take-last 3 splitS))))
            %))
       constraints))

(defn getSchema
  "Get constraints and indexes in neo4j."
  [& {:keys [:tx]
      :or {:tx nil}}]
  (let [constraintQ {:query "CALL db.constraints()"
                     :parameters {}}
        indexQ {:query "CALL db.indexes()"
                :parameters {}}]
    {:constraints (into #{} (fixConstraintQueries (if (nil? tx)
                                                    (first ((runQuery constraintQ) :results))
                                                    (sQ tx
                                                        (constraintQ :query)
                                                        (constraintQ :parameters)))))
     :indexes (into #{} (if (nil? tx)
                          (first ((runQuery indexQ) :results))
                          (sQ tx
                              (indexQ :query)
                              (indexQ :parameters))))}))
