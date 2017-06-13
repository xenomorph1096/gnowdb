(ns gnowdb.neo4j.gneo_test1
  (:require [clojure.test :refer :all]
              [gnowdb.neo4j.gneo :refer :all]
            [gnowdb.neo4j.gdriver :refer :all]

  
            )
)

(deftest deleteRelations-test

    (testing "Creating nodes for testing"
        (runQuery {:query "create (n:test)-[r:rel1]->(n1:test1),(n3:test3 {name:{b}})-[:rel2]->(n4:test),(n5:test3)-[:rel3]->(n6:test {name:{a}})" :parameters {"a" "t-db" "b" "t-db1"}}

    )
        )

    (testing "Error in deleting using only labels"
    (is (= {:summaryMap
 {:relationshipsCreated 0,
  :containsUpdates true,
  :nodesCreated 0,
  :nodesDeleted 0,
  :indexesRemoved 0,
  :labelsRemoved 0,
  :constraintsAdded 0,
  :propertiesSet 0,
  :labelsAdded 0,
  :constraintsRemoved 0,
  :indexesAdded 0,
  :relationshipsDeleted 1},
 :summaryString "ContainsUpdates :true ;RelationshipsDeleted :1 ;"} 
 (deleteRelations :fromNodeLabel ["test"] :toNodeLabel ["test1"])
        )
    )
    )

    (testing "Error in deleting using labels and parameters"
      (is (= {:summaryMap
 {:relationshipsCreated 0,
  :containsUpdates true,
  :nodesCreated 0,
  :nodesDeleted 0,
  :indexesRemoved 0,
  :labelsRemoved 0,
  :constraintsAdded 0,
  :propertiesSet 0,
  :labelsAdded 0,
  :constraintsRemoved 0,
  :indexesAdded 0,
  :relationshipsDeleted 1},
 :summaryString "ContainsUpdates :true ;RelationshipsDeleted :1 ;"} 
 (deleteRelations :fromNodeLabel ["test3"] :toNodeParameters {"name" "t-db"} :toNodeLabel ["test"])
          )
      )

  )
    

)

(deftest getNeighborhood-test
  (testing "Error in returning neighbors using only label"
    (is (= (select-keys {:labels ["test3"],
 :properties {"name" "t-db1"},
 :outNodes `({:labels "rel2", :properties {}, :toNode 22}),
 :inNodes `()} [:labels :properties]) (select-keys (getNeighborhood :label "test3" :parameters {"name" "t-db1"}) [:labels :properties])
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
  (select-keys (runQuery {:query "match (n:test),(n1:test1),(n3:test3) detach delete n,n1,n3" :parameters {}}) [:results]) 
            )
        )

    )

)

