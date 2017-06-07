(ns gnowdb.neo4j.gneo_test
  (:require [clojure.test :refer :all]
  
            [gnowdb.neo4j.gneo :refer :all]
            [gnowdb.neo4j.gdriver :refer :all]

  
            )
)

(deftest getRelations-test

	(testing "Creating nodes to run the tests"
		(is (= (select-keys {:results [() () ()],
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
  "RelationshipsCreated :1 ;ContainsUpdates :true ;NodesCreated :2 ;PropertiesSet :1 ;LabelsAdded :2 ;"}} [:results]) 
  (select-keys (runQuery {:query "create (n:test1 {name:{b}})-[:rel1]->(n1:test)" :parameters {"b" "t-db21"}} {:query "create (n2:test)-[:rel2]->(n3:test)" :parameters {}} 
   {:query "create (n:test3 {name:{a}})-[:rel3 {name:{b}}]->(n1:test)" :parameters {"a" "t-db-20" "b" "rel_name3"}}
  	) [:results])
			)
		)
	)

	(testing "Error in getting a relationship:"

     (testing "matching by to and from node labels-case1"
    (is (= (select-keys {:labels "rel1", :properties {}, :fromNode 41 , :toNode 42} [:labels :properties]) 
      (select-keys (first (getRelations :fromNodeLabel ["test1"] :toNodeLabel ["test"] )) [:labels :properties])
      )
    
    )
     )

     (testing "matching by to and from node labels-case2"
    (is (= (select-keys {:labels "rel2", :properties {}, :fromNode 41 , :toNode 42} [:labels :properties]) 
      (select-keys (first (getRelations :fromNodeLabel ["test"] )) [:labels :properties])
      )
    
    )
     )

     (testing "matching by relationship parameter"
      (is (= (select-keys {:labels "rel3", :properties {"name" "rel_name3"}, :fromNode 41 , :toNode 42} [:labels :properties]) 
        (select-keys (first (getRelations :relationshipParameters {"name" "rel_name3"})) [:labels :properties])
        )
      )

     )

     (testing "matching by node parameter"
    (is (= (select-keys {:labels "rel3", :properties {"name" "rel_name3"}, :fromNode 41 , :toNode 42} [:labels :properties]) 
        (select-keys (first (getRelations :fromNodeParameters {"name" "t-db-20"})) [:labels :properties])
        )

    
    )
     )

     (testing "matching by relationship type"
      (is (= (select-keys {:labels "rel3", :properties {"name" "rel_name3"}, :fromNode 41 , :toNode 42} [:labels :properties]) 
        (select-keys (first (getRelations :relationshipType "rel3")) [:labels :properties])
        )
      )
     )



     
     )



  (testing "Deleting all changes"
        (is (= (select-keys {:results [()],
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
  "ContainsUpdates :true ;NodesDeleted :2 ;RelationshipsDeleted :1 ;"}} [:results])
  (select-keys (runQuery {:query "match (n) detach delete n" :parameters {}}) [:results]) 
            )
        )

    )


	
	  

	)
(run-tests)
