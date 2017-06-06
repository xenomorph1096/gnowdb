(ns gnowdb.neo4j.gdriver_test
  (:require [clojure.test :refer :all]
  
            [gnowdb.neo4j.gdriver :refer :all]

  
            )
  )

(deftest runQuery-test
		(testing "Error in node creation:"
			(testing "Empty Query"
				(is (= {:results [],
 :summary
 {:summaryMap
  {:relationshipsCreated 0,
   :containsUpdates false,
   :nodesCreated 0,
   :nodesDeleted 0,
   :indexesRemoved 0,
   :labelsRemoved 0,
   :constraintsAdded 0,
   :propertiesSet 0,
   :labelsAdded 0,
   :constraintsRemoved 0,
   :indexesAdded 0,
   :relationshipsDeleted 0},
  :summaryString "ContainsUpdates :false ;"}}
 (runQuery)

					)
				)
		    )

		    (testing "no node name only parameters"
		    	(is (= {:results [()],
 :summary
 {:summaryMap
  {:relationshipsCreated 0,
   :containsUpdates true,
   :nodesCreated 1,
   :nodesDeleted 0,
   :indexesRemoved 0,
   :labelsRemoved 0,
   :constraintsAdded 0,
   :propertiesSet 0,
   :labelsAdded 1,
   :constraintsRemoved 0,
   :indexesAdded 0,
   :relationshipsDeleted 0},
  :summaryString
  "ContainsUpdates :true ;NodesCreated :1 ;LabelsAdded :1 ;"}}
 (runQuery {:query "Create (:test)" :parameters {"a" "test_case2"}})
		    	    )
		    	)
		    )

		    (testing "no parameter" 
		    	(is (= {:results [()],
 :summary
 {:summaryMap
  {:relationshipsCreated 0,
   :containsUpdates true,
   :nodesCreated 1,
   :nodesDeleted 0,
   :indexesRemoved 0,
   :labelsRemoved 0,
   :constraintsAdded 0,
   :propertiesSet 0,
   :labelsAdded 1,
   :constraintsRemoved 0,
   :indexesAdded 0,
   :relationshipsDeleted 0},
  :summaryString
  "ContainsUpdates :true ;NodesCreated :1 ;LabelsAdded :1 ;"}} 
  (runQuery {:query "Create (test_case3:test)" :parameters {}})

		    	   )
		      )

        )
		    (testing "two nodes simultaneously"
		    	(is (= {:results [()],
 :summary
 {:summaryMap
  {:relationshipsCreated 0,
   :containsUpdates true,
   :nodesCreated 2,
   :nodesDeleted 0,
   :indexesRemoved 0,
   :labelsRemoved 0,
   :constraintsAdded 0,
   :propertiesSet 0,
   :labelsAdded 2,
   :constraintsRemoved 0,
   :indexesAdded 0,
   :relationshipsDeleted 0},
  :summaryString
  "ContainsUpdates :true ;NodesCreated :2 ;LabelsAdded :2 ;"}} 
  (runQuery {:query "Create (node1:test),(node2:test)" :parameters {}})
		    	    )
		    
		        )

		    )

		    (testing "two parameters for single node"
		    	(is (= {:results [()],
 :summary
 {:summaryMap
  {:relationshipsCreated 0,
   :containsUpdates true,
   :nodesCreated 1,
   :nodesDeleted 0,
   :indexesRemoved 0,
   :labelsRemoved 0,
   :constraintsAdded 0,
   :propertiesSet 2,
   :labelsAdded 1,
   :constraintsRemoved 0,
   :indexesAdded 0,
   :relationshipsDeleted 0},
  :summaryString
  "ContainsUpdates :true ;NodesCreated :1 ;PropertiesSet :2 ;LabelsAdded :1 ;"}} 
  (runQuery {:query "Create (node2:test {name:{a},place:{b}})" :parameters {"a" "test_case4" "b" "Mumbai"}}) 
		    		)
		    	
		    	)

		    )

		    (testing "more than one nodes each with more than one parameter"
		    	(is (= {:results [()],
 :summary
 {:summaryMap
  {:relationshipsCreated 0,
   :containsUpdates true,
   :nodesCreated 2,
   :nodesDeleted 0,
   :indexesRemoved 0,
   :labelsRemoved 0,
   :constraintsAdded 0,
   :propertiesSet 4,
   :labelsAdded 2,
   :constraintsRemoved 0,
   :indexesAdded 0,
   :relationshipsDeleted 0},
  :summaryString
  "ContainsUpdates :true ;NodesCreated :2 ;PropertiesSet :4 ;LabelsAdded :2 ;"}} 
  (runQuery {:query "Create (node2:test {name:{a},place:{b}}), (node3:test {name:{c}, place:{d}})" :parameters {"a" "test_case4" "b" "Mumbai" "c" "test_case5" "d" "mumbai"}})
		    		)
		    	)
		    )
      )
        (testing "Error in creating Relationships:"
        	

        	(testing "Relationship query between two nodes"
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
   :propertiesSet 4,
   :labelsAdded 2,
   :constraintsRemoved 0,
   :indexesAdded 0,
   :relationshipsDeleted 0},
  :summaryString
  "RelationshipsCreated :1 ;ContainsUpdates :true ;NodesCreated :2 ;PropertiesSet :4 ;LabelsAdded :2 ;"}} 
  (runQuery {:query "Create (node2:test {name:{a},place:{b}}), (node3:test {name:{c}, place:{d}}), (node2)-[:r]->(node3)" :parameters {"a" "test_case 6" "b" "Mumbai" "c" "test_case5" "d" "mumbai"}}) 
        			) 
        		)

        	)

     
        	(testing "Two way relationship"
        		(is (= {:results [()],
 :summary
 {:summaryMap
  {:relationshipsCreated 2,
   :containsUpdates true,
   :nodesCreated 2,
   :nodesDeleted 0,
   :indexesRemoved 0,
   :labelsRemoved 0,
   :constraintsAdded 0,
   :propertiesSet 4,
   :labelsAdded 2,
   :constraintsRemoved 0,
   :indexesAdded 0,
   :relationshipsDeleted 0},
  :summaryString
  "RelationshipsCreated :2 ;ContainsUpdates :true ;NodesCreated :2 ;PropertiesSet :4 ;LabelsAdded :2 ;"}} 
  (runQuery {:query "Create (node2:test {name:{a},place:{b}}), (node3:test {name:{c}, place:{d}}), (node2)-[:r]->(node3), (node3)-[:r]->(node2)" :parameters {"a" "test_case 6" "b" "Mumbai" "c" "test_case5" "d" "mumbai"}}) 

        			)
        		)
        	
        	)

        	(testing "relationship with parameters"
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
   :propertiesSet 6,
   :labelsAdded 2,
   :constraintsRemoved 0,
   :indexesAdded 0,
   :relationshipsDeleted 0},
  :summaryString
  "RelationshipsCreated :1 ;ContainsUpdates :true ;NodesCreated :2 ;PropertiesSet :6 ;LabelsAdded :2 ;"}} 
  (runQuery {:query "Create (node2:test {name:{a},place:{b}}), (node3:test {name:{c}, place:{d}}), (node2)-[:r {name:{e}, type:{f}}]->(node3)" :parameters {"a" "test_case 6" "b" "Mumbai" "c" "test_case5" "d" "mumbai" "e" "test_rel1" "f" "test_rel2"}})
        			)
        		)
        	)

                )

        
)
		    
(run-tests)