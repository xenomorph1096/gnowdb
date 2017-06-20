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
        (runQuery {:query "match (n:test),(n1:test1),(n3:test3) detach delete n,n1,n3" :parameters {}})

    )
  
  )


 (deftest createClass-test

 (testing " Error in Creating Class"
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
   :propertiesSet 5,
   :labelsAdded 1,
   :constraintsRemoved 0,
   :indexesAdded 0,
   :relationshipsDeleted 0},
  :summaryString
  "ContainsUpdates :true ;NodesCreated :1 ;PropertiesSet :5 ;LabelsAdded :1 ;"}}
  (createClass :className "t-db22" :classType "NODE" :isAbstract? true :subClassOf [] :properties {"tag" "test"} :execute? true)
 )
    )
    )

  (testing "Error in Creating a sub-class"
    (is (= {:results
 `({:results [() ()],
   :summary
   {:summaryMap
    {:relationshipsCreated 1,
     :containsUpdates true,
     :nodesCreated 1,
     :nodesDeleted 0,
     :indexesRemoved 0,
     :labelsRemoved 0,
     :constraintsAdded 0,
     :propertiesSet 5,
     :labelsAdded 1,
     :constraintsRemoved 0,
     :indexesAdded 0,
     :relationshipsDeleted 0},
    :summaryString
    "RelationshipsCreated :1 ;ContainsUpdates :true ;NodesCreated :1 ;PropertiesSet :5 ;LabelsAdded :1 ;"}}),
 :summary
 {:summaryMap
  {:relationshipsCreated 1,
   :containsUpdates true,
   :nodesCreated 1,
   :nodesDeleted 0,
   :indexesRemoved 0,
   :labelsRemoved 0,
   :constraintsAdded 0,
   :propertiesSet 5,
   :labelsAdded 1,
   :constraintsRemoved 0,
   :indexesAdded 0,
   :relationshipsDeleted 0},
  :summaryString
  "RelationshipsCreated :1 ;ContainsUpdates :true ;NodesCreated :1 ;PropertiesSet :5 ;LabelsAdded :1 ;"}}

 (createClass :className "t-db23" :classType "NODE" :isAbstract? true :subClassOf ["t-db22"] :properties {"tag" "test"} :execute? true)
        )
    )

  )

    (testing "Error in Returning a query (execute? false)"
      (is (= (select-keys {:query "MERGE (node:Class { className:{className}, classType:{classType}, isAbstract:{isAbstract}, UUID:{UUID} } )", :parameters {"className" "t-db24", "classType" "NODE", "isAbstract" true, "UUID" "8f1d44e1-a3c1-45aa-a428-4b3dd00f6eb3"}} [:query]) 
        (select-keys (createClass :className "t-db24" :classType "NODE" :isAbstract? true :subClassOf [] :properties {} :execute? false) [:query])
        )
      )


    )

    
    (testing "Error in creating nodes with two properties"
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
   :propertiesSet 7,
   :labelsAdded 1,
   :constraintsRemoved 0,
   :indexesAdded 0,
   :relationshipsDeleted 0},
  :summaryString
  "ContainsUpdates :true ;NodesCreated :1 ;PropertiesSet :7 ;LabelsAdded :1 ;"}} 
 (createClass :className "t-db24" :classType "NODE" :isAbstract? true :subClassOf [] :properties {"pr1" "val1" "pr2" "val2" "tag" "test"} :execute? true)
        )
      )
    )

    (testing "Error in creating a sub-class when super-class doesn`t exist"
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
  :summaryString "ContainsUpdates :false ;"}} (createClass :className "t-db23" :classType "NODE" :isAbstract? false :subClassOf ["t-db25"] :properties {"pr1" "val1" "pr2" "val2"} :execute? true) 
        )
      )

    )

    (testing "Creating a sub class of a class which alredy has other sub-classes"
      (is (= {:results
 `({:results [() ()],
   :summary
   {:summaryMap
    {:relationshipsCreated 1,
     :containsUpdates true,
     :nodesCreated 1,
     :nodesDeleted 0,
     :indexesRemoved 0,
     :labelsRemoved 0,
     :constraintsAdded 0,
     :propertiesSet 5,
     :labelsAdded 1,
     :constraintsRemoved 0,
     :indexesAdded 0,
     :relationshipsDeleted 0},
    :summaryString
    "RelationshipsCreated :1 ;ContainsUpdates :true ;NodesCreated :1 ;PropertiesSet :5 ;LabelsAdded :1 ;"}}),
 :summary
 {:summaryMap
  {:relationshipsCreated 1,
   :containsUpdates true,
   :nodesCreated 1,
   :nodesDeleted 0,
   :indexesRemoved 0,
   :labelsRemoved 0,
   :constraintsAdded 0,
   :propertiesSet 5,
   :labelsAdded 1,
   :constraintsRemoved 0,
   :indexesAdded 0,
   :relationshipsDeleted 0},
  :summaryString
  "RelationshipsCreated :1 ;ContainsUpdates :true ;NodesCreated :1 ;PropertiesSet :5 ;LabelsAdded :1 ;"}}
 
 (createClass :className "t-db26" :classType "NODE" :isAbstract? true :subClassOf ["t-db22"] :properties {"tag" "test"} :execute? true)
        )
      )

    )

    



    


    (testing "Deleting all changes2"
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
  (select-keys (runQuery {:query "match (n {tag:{a}}) detach delete n" :parameters {"a" "test"}}) [:results]) 
            )
        )

    )


    


    
  )
(deftest getAllLabels-Test
    (testing "Error in running the function"
        (getAllLabels)
    )




)

(deftest getAllNodes-test
    (testing "Error in running the function"
        (getAllNodes)
    )

    (testing "Deleting all changes2"
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
  (select-keys (runQuery {:query "match (n {tag:{a}}) detach delete n" :parameters {"a" "test"}}) [:results]) 
            )
        )

    )
)

(deftest createNewNode-test
    (testing "Error in creating a new node"
        (is (= {:summaryMap
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
 "ContainsUpdates :true ;NodesCreated :1 ;PropertiesSet :2 ;LabelsAdded :1 ;"}
(createNewNode :label "test" :parameters {"name" "t-db1"} :execute? true :unique? true)
            )
        )

    )

    (testing "Error in creating a node with fewer arguements"
        (is (= {:summaryMap
 {:relationshipsCreated 0,
  :containsUpdates true,
  :nodesCreated 1,
  :nodesDeleted 0,
  :indexesRemoved 0,
  :labelsRemoved 0,
  :constraintsAdded 0,
  :propertiesSet 1,
  :labelsAdded 1,
  :constraintsRemoved 0,
  :indexesAdded 0,
  :relationshipsDeleted 0},
 :summaryString
 "ContainsUpdates :true ;NodesCreated :1 ;PropertiesSet :1 ;LabelsAdded :1 ;"} 
 (createNewNode :label "test")
            )
        )

    )

;;;;;;;;;;uuidEnabled not tested





)

(deftest addStringToMapKeys-test
    (testing "Error in adding string to map keys(strings)"
        (is (= {"test1suff" "test2suff"}
            (addStringToMapKeys {"test1" "value1" "test2" "value2"} "suff")
            )
        )
    )

    (testing "Error in adding string to map keys(numbers)"
        (is (= {"1suff" "2suff"}
            (addStringToMapKeys {1 "value1" 2 "value2"} "suff")
            )
        )
    )
)

(deftest createEditString-test
    (testing "Error in editing string 1"
        (is (= " SET n.height = {height} ,n.streng = {strength}"
            (createEditString :varName "n" :editPropertyList '("height" "strength") :characteristicString "th")
            )
        )
    )

    (testing "Error in editing string 2"
        (is (= " SET class.name = {name1} ,class.prop = {prop1}"
            (createEditString :varName "class" :editPropertyList '("name1" "prop1") :characteristicString "1")
            )
        )
    )
)



(run-tests)

   

