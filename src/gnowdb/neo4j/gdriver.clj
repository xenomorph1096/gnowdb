(ns gnowdb.neo4j.gdriver
  (:gen-class)
  (:require [clojure.set :as clojure.set]
            [clojure.java.io :as io]
            [clojure.string :as clojure.string]))

(import '[org.neo4j.driver.v1 Driver AuthTokens GraphDatabase Record Session StatementResult Transaction Values]
        '[java.io PushbackReader])

(defn- getNeo4jDBDetails
	"Get Neo4jDB Details :bolt-url,:username,:password"
	[]
	(with-open [r (io/reader "src/gnowdb/neo4j/gconf.clj")]
		(read (PushbackReader. r))
	)
)

(defn- getDriver
	"Get neo4j Database Driver"
	[]
	(let [neo4jDBDetails (getNeo4jDBDetails)]
	(GraphDatabase/driver (neo4jDBDetails :bolt-url) (AuthTokens/basic (neo4jDBDetails :username) (neo4jDBDetails :password)))
	)
)

(defn- newTransaction
	"Get a new Transaction to query the Database"
	[]
	(.beginTransaction (.session (getDriver)))
)

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
    {:summaryMap sumMap :summaryString (createSummaryString sumMap)}))

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

(defn runQuery
	"Takes a list of queries and executes them. Returns a map with all records and summary of operations"
	[queriesList]
	(let [transaction (newTransaction)]
		(try
			(reduce
				(fn [resultMap queryMap]
					(let [statementResult (.run transaction (queryMap :query) (java.util.HashMap. (queryMap :parameters)))]
						{:results (conj (resultMap :results) (.list statementResult)) :summary (getCombinedFullSummary [(resultMap :summary) (getFullSummary statementResult)])}
					)
				)
				{:results [] :summary {}}
				queriesList
			)
			(catch Exception e (.failure transaction) (.printStackTrace e))
			(finally (.success transaction) (.close transaction))
		)
	)
)

