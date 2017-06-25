(ns gnowdb.neo4j.queryAggregator
	(:gen-class)
	(:require [gnowdb.neo4j.gdriver :as gdriver]
	)
)

(defn queryAggregator
	[]
	(atom {:queryList [] :lastUpdate "" :IDMaps #{}})
)

(defn addQueries
	[aggregator queryType & queries]
	(let
		[queryList
			(if (not= (@aggregator :lastUpdate) queryType)
				(conj (@aggregator :queryList) (vec queries))
				(into (last (@aggregator :queryList)) queries)
			)
		]
		(reset! aggregator {
			:queryList queryList
			:lastUpdate queryType 
			:IDMaps 	(into
						(@aggregator :IDMaps)
						(map #(% :IDMap) queries)
					)
		})
	)
)

(defn flushQueries
	[aggregator]
	(apply gdriver/runTransactions (aggregator :queryList))
	;RCS Trigger goes here with UUIDs of all nodes UUID can be xtracted from the neighborhood
	(reset! aggregator {:queryList [] :lastUpdate "" :IDMaps #{}})
)