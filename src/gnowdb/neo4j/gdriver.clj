(ns gnowdb.neo4j.gdriver
  (:gen-class)
  (:require [clojure.set :as clojure.set]
            [clojure.java.io :as io]
            [clojure.string :as clojure.string]
            [async-watch.core :refer [changes-in cancel-changes]]))
			

(import '[org.neo4j.driver.v1 Driver AuthTokens GraphDatabase Record Session StatementResult Transaction Values]
        '[java.io PushbackReader])


(def ^{:private true} getNeo4jDBDetails 
	(with-open [r (io/reader "src/gnowdb/neo4j/gconf.clj")]
		(read (PushbackReader. r)
		)
	)
)


(defn- getDriver
	"Get neo4j Database Driver"
	[]
	(GraphDatabase/driver (getNeo4jDBDetails :bolt-url) (AuthTokens/basic (getNeo4jDBDetails :username) (getNeo4jDBDetails :password)))
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
			(instance? org.neo4j.driver.v1.types.Node data) {:labels (.labels data) :properties (.asMap data)}
			(instance? org.neo4j.driver.v1.types.Relationship data) {:labels (.type data) :properties (.asMap data) :fromNode (.startNodeId data) :toNode (.endNodeId data)}
			(instance? org.neo4j.driver.v1.types.Path data) {:start (parse (.start data)):end (parse (.end data)) :segments (map (fn [segment] {:start (parse (.start segment)) :end (parse (.end segment)) :relationship (parse (.relationship segment))}) data) :length (reduce (fn [counter, data] (+ counter 1)) 0 data)}
			:else data
	)
)

(defn runQuery
	"Takes a list of queries and executes them. Returns a map with all records and summary of operations iff all operations are successful otherwise fails.
	Input Format: {:query <query> :parameters <Map of parameters as string key-value pairs>} ...
	Output Format: {:results [(result 1) (result 2) .....] :summary <Summary Map>}
	In case of failure, {:results [] :summary <default full summary>}"
	[& queriesList]
	(let [
			driver (getDriver)
			session (.session driver)
			transaction (.beginTransaction session)
		 ]
		(try
			(let
				[finalResult (reduce
					(fn [resultMap queryMap]
						(let [statementResult (.run transaction (queryMap :query) (java.util.HashMap. (queryMap :parameters)))]
							{:results (conj 
								(resultMap :results) 
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
									(.list statementResult)
								)) 
							 :summary (getCombinedFullSummary [(resultMap :summary) (getFullSummary statementResult)])
							}
						)
					)
					{:results [] :summary (getCombinedFullSummary [])}
					queriesList
				)]
				(.success transaction)
				finalResult
			)
			(catch Throwable e (.failure transaction) {:results [] :summary {:summaryMap {} :summaryString (.toString e)}})
			(finally (.close transaction) (.close session) (.close driver))
		)
	)
)

(let [changes (changes-in ["src/gnowdb/neo4j"])]
	(clojure.core.async/go 
		(while true
			(let [[op filename] (<! changes)]
				;; op will be one of :create, :modify or :delete
				(if (= filename "src/gnowdb/neo4j/gconf.clj")
					(if (= op :delete)
						(cancel-changes)
						(def ^{:private true} getNeo4jDBDetails 
							(with-open [r (io/reader "src/gnowdb/neo4j/gconf.clj")]
								(read (PushbackReader. r)
								)
							)
						)
					)
				)
			)
		)
	)
)