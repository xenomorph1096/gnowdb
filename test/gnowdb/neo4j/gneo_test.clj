(ns gnowdb.neo4j.gneo_test
  (:require [clojure.test :refer :all]
  
            [gnowdb.neo4j.gneo :refer :all]
            [gnowdb.neo4j.gdriver :refer :all]

  
            )
)

(deftest getRelations-test
	(testing "Creating nodes to run the tests"
		(is (= {:results [()],
 :summary
 {:summaryMap
  {:relationshipsCreated 1,
   :containsUpdates true,
   :nodesCreated 2,
   :nodesDeleted 0,
   :indexesRemoved 0,
   :labelsRemoved 0,
   :constraintsAdded 0,
   :propertiesSet 1,
   :labelsAdded 2,
   :constraintsRemoved 0,
   :indexesAdded 0,
   :relationshipsDeleted 0},
  :summaryString
  "RelationshipsCreated :1 ;ContainsUpdates :true ;NodesCreated :2 ;PropertiesSet :1 ;LabelsAdded :2 ;"}} 
  (runQuery {:query "create (n:test {name:{a}})-[r:r]->(n1:test)" :parameters {"a" "test_case5"}})
			)
		)
	)

	   (testing "Error in getting a relationship"
		(is (= (select-keys {:labels "r", :properties {}, :fromNode 41 , :toNode 42} [:labels :properties]) 
			(select-keys (first (getRelations :fromNodeLabel ["test"] :toNodeLabel ["test"] :fromNodeparameters {"name" "test_case5"})) [:labels :properties])
			)
		
		)

	)

	(testing "Deleting all changes"
        (is (= {:results [()],
 :summary
 {:summaryMap
  {:relationshipsCreated 0,
   :containsUpdates true,
   :nodesCreated 0,
   :nodesDeleted 2,
   :indexesRemoved 0,
   :labelsRemoved 0,
   :constraintsAdded 0,
   :propertiesSet 0,
   :labelsAdded 0,
   :constraintsRemoved 0,
   :indexesAdded 0,
   :relationshipsDeleted 1},
  :summaryString
  "ContainsUpdates :true ;NodesDeleted :2 ;RelationshipsDeleted :1 ;"}} 
  (runQuery {:query "match (n) detach delete n" :parameters {}}) 
            )
        )

    )

	)
(run-tests)
