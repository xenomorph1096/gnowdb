(ns gnowdb.neo4j.gneo_test
  (:require [clojure.test :refer :all]
            [gnowdb.neo4j.gneo :refer :all]
            [gnowdb.neo4j.gdriver :refer :all]

            
            )
  )

(deftest getRelations-test

  (testing "Creating nodes to run the tests"
    (is 
     (= (select-keys {:results [() () ()],
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
        (select-keys 
         (runQuery {:query "create (n:test1 {name:{b}})-[:rel1]->(n1:test)" :parameters {"b" "t-db21"}} {:query "create (n2:test)-[:rel2]->(n3:test)" :parameters {}} 
                   {:query "create (n:test3 {name:{a}})-[:rel3 {name:{b}}]->(n1:test)" :parameters {"a" "t-db-20" "b" "rel_name3"}}
                   ) [:results]
         )
        )
     )
    )

  (testing "Error in getting a relationship:"
    (testing "matching by to and from node labels-case1"
      (is 
       (=  (select-keys {:labels "rel1", :properties {}, :fromNode 41 , :toNode 42} [:labels :properties]) 
           (select-keys (first (getRelations :fromNodeLabels ["test1"] :toNodeLabels ["test"] )) [:labels :properties])
           )
       )
      )

    (testing "matching by to and from node labels-case2"
      (is 
       (=  (select-keys {:labels "rel2", :properties {}, :fromNode 41 , :toNode 42} [:labels :properties]) 
           (select-keys (first (getRelations :fromNodeLabels ["test"] )) [:labels :properties])
           )
       )
      )

    (testing "matching by relationship parameter"
      (is 
       (=  (select-keys {:labels "rel3", :properties {"name" "rel_name3"}, :fromNode 41 , :toNode 42} [:labels :properties]) 
           (select-keys (first (getRelations :relationshipParameters {"name" "rel_name3"})) [:labels :properties])
           )
       )
      )

    (testing "matching by node parameter"
      (is 
       (=  (select-keys {:labels "rel3", :properties {"name" "rel_name3"}, :fromNode 41 , :toNode 42} [:labels :properties]) 
           (select-keys (first (getRelations :fromNodeParameters {"name" "t-db-20"})) [:labels :properties])
           )
       )
      )

    (testing "matching by relationship type"
      (is 
       (= (select-keys {:labels "rel3", :properties {"name" "rel_name3"}, :fromNode 41 , :toNode 42} [:labels :properties]) 
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
    (is 
     (= {:results [()],
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
    (is 
     (= {:results
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
    (is 
     (= (select-keys {:query "MERGE (node:`Class` { className:{className}, classType:{classType}, isAbstract:{isAbstract}, UUID:{UUID} } )", :parameters {"className" "t-db24", "classType" "NODE", "isAbstract" true, "UUID" "d1c346b0-17ed-46f1-b022-6273402048b4"}} [:query]) 
        (select-keys (createClass :className "t-db24" :classType "NODE" :isAbstract? true :subClassOf [] :properties {} :execute? false) [:query])
        )
     )
    )

  (testing "Error in creating nodes with two properties"
    (is 
     (= {:results [()],
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
    (is 
     (= {:results [],
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
          :summaryString 
          "ContainsUpdates :false ;"}} 
        (createClass :className "t-db23" :classType "NODE" :isAbstract? false :subClassOf ["t-db25"] :properties {"pr1" "val1" "pr2" "val2"} :execute? true) 
        )
     )
    )

  (testing "Creating a sub class of a class which alredy has other sub-classes"
    (is 
     (= {:results
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

  (createClass :className "tdb23" :classType "NODE" :isAbstract? true :subClassOf [] :properties {"tag" "test"} :execute? true)
  (createAttributeType :_name "small" :_datatype "java.lang.String");;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
  (addClassAT :_atname "small" :className "tdb23")
  
  (testing " Error in creating subclass of a class with an attribute."
    (is 
     (= {:results
         '({:results [() () ()],
            :summary
            {:summaryMap
             {:relationshipsCreated 2,
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
             "RelationshipsCreated :2 ;ContainsUpdates :true ;NodesCreated :1 ;PropertiesSet :5 ;LabelsAdded :1 ;"}}),
         :summary
         {:summaryMap
          {:relationshipsCreated 2,
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
          "RelationshipsCreated :2 ;ContainsUpdates :true ;NodesCreated :1 ;PropertiesSet :5 ;LabelsAdded :1 ;"}}
        (createClass :className "rdb21" :classType "NODE" :isAbstract? true :subClassOf ["tdb23"] :properties {"tag" "test"}  :execute? true)
        )
     )
    )
  
  (createCustomFunction :fnName "func" :fnString "(fn [value1 value2] (println value1))")
  (addClassCC :fnName "func" :atList ["small"] :constraintValue "none" :className "tdb23")
  
  (testing " Error in creating subclass of a class with a custom constraint."
    (is 
     (= {:results
         '({:results [() () () ()],
            :summary
            {:summaryMap
             {:relationshipsCreated 3,
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
             "RelationshipsCreated :3 ;ContainsUpdates :true ;NodesCreated :1 ;PropertiesSet :7 ;LabelsAdded :1 ;"}}),
         :summary
         {:summaryMap
          {:relationshipsCreated 3,
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
          "RelationshipsCreated :3 ;ContainsUpdates :true ;NodesCreated :1 ;PropertiesSet :7 ;LabelsAdded :1 ;"}}
        (createClass :className "rdb23" :classType "NODE" :isAbstract? true :subClassOf ["tdb23"] :properties {"tag" "test"}  :execute? true)
        )
     )
    )

  (createClass :className "rel" :classType "RELATION" :isAbstract? true :subClassOf [] :properties {"tag" "test"}  :execute? true)
  (addRelApplicableType :className "rel" :applicationType "Source" :applicableClassName "tdb23")
  (createClass :className "rel1" :classType "RELATION" :isAbstract? true :subClassOf [] :properties {"tag" "test"}  :execute? true)
  (addRelApplicableType :className "rel1" :applicationType "Target" :applicableClassName "tdb23")

  (testing " Error in creating subclass of a class with a applicable relation types."
    (is 
     (= {:results
         '({:results [() () () () () ()],
            :summary
            {:summaryMap
             {:relationshipsCreated 5,
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
             "RelationshipsCreated :5 ;ContainsUpdates :true ;NodesCreated :1 ;PropertiesSet :7 ;LabelsAdded :1 ;"}}),
         :summary
         {:summaryMap
          {:relationshipsCreated 5,
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
          "RelationshipsCreated :5 ;ContainsUpdates :true ;NodesCreated :1 ;PropertiesSet :7 ;LabelsAdded :1 ;"}}
        (createClass :className "rdb24" :classType "NODE" :isAbstract? true :subClassOf ["tdb23"] :properties {"tag" "test"}  :execute? true)
        )
     )
    )

  (testing "Deleting all changes2"
    (is 
     (= (select-keys {:results [()],
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
  
                                        ; (createClass :className "tdb23" :classType "NODE" :isAbstract? true :subClassOf [] :properties {"tag" "test"} :execute? true)
                                        ; (addClassAT :_atname "small" :className "tdb23")
                                        ; ;(createNeoConstraint :constraintType "UNIQUE" :constraintTarget "NODE")

                                        ; (addClassNC :constraintType "EXISTANCE" :constraintTarget "NODE" :constraintValue "small" :className "tdb23")

                                        ; (testing " Error in creating subclass of a class with a neo constraint."
                                        ;   (is 
                                        ;     (= {:results
                                        ;      '({:results [() () ()],
                                        ;        :summary
                                        ;        {:summaryMap
                                        ;         {:relationshipsCreated 2,
                                        ;          :containsUpdates true,
                                        ;          :nodesCreated 1,
                                        ;          :nodesDeleted 0,
                                        ;          :indexesRemoved 0,
                                        ;          :labelsRemoved 0,
                                        ;          :constraintsAdded 0,
                                        ;          :propertiesSet 5,
                                        ;          :labelsAdded 1,
                                        ;          :constraintsRemoved 0,
                                        ;          :indexesAdded 0,
                                        ;          :relationshipsDeleted 0},
                                        ;         :summaryString
                                        ;         "RelationshipsCreated :2 ;ContainsUpdates :true ;NodesCreated :1 ;PropertiesSet :5 ;LabelsAdded :1 ;"}}
                                        ;       {:results [()],
                                        ;        :summary
                                        ;        {:summaryMap
                                        ;         {:relationshipsCreated 0,
                                        ;          :containsUpdates true,
                                        ;          :nodesCreated 0,
                                        ;          :nodesDeleted 0,
                                        ;          :indexesRemoved 0,
                                        ;          :labelsRemoved 0,
                                        ;          :constraintsAdded 1,
                                        ;          :propertiesSet 0,
                                        ;          :labelsAdded 0,
                                        ;          :constraintsRemoved 0,
                                        ;          :indexesAdded 0,
                                        ;          :relationshipsDeleted 0},
                                        ;         :summaryString "ContainsUpdates :true ;ConstraintsAdded :1 ;"}}
                                        ;       {:results [()],
                                        ;        :summary
                                        ;        {:summaryMap
                                        ;         {:relationshipsCreated 1,
                                        ;          :containsUpdates true,
                                        ;          :nodesCreated 0,
                                        ;          :nodesDeleted 0,
                                        ;          :indexesRemoved 0,
                                        ;          :labelsRemoved 0,
                                        ;          :constraintsAdded 0,
                                        ;          :propertiesSet 1,
                                        ;          :labelsAdded 0,
                                        ;          :constraintsRemoved 0,
                                        ;          :indexesAdded 0,
                                        ;          :relationshipsDeleted 0},
                                        ;         :summaryString
                                        ;         "RelationshipsCreated :1 ;ContainsUpdates :true ;PropertiesSet :1 ;"}}),
                                        ;      :summary
                                        ;      {:summaryMap
                                        ;       {:relationshipsCreated 3,
                                        ;        :containsUpdates true,
                                        ;        :nodesCreated 1,
                                        ;        :nodesDeleted 0,
                                        ;        :indexesRemoved 0,
                                        ;        :labelsRemoved 0,
                                        ;        :constraintsAdded 1,
                                        ;        :propertiesSet 6,
                                        ;        :labelsAdded 1,
                                        ;        :constraintsRemoved 0,
                                        ;        :indexesAdded 0,
                                        ;        :relationshipsDeleted 0},
                                        ;       :summaryString
                                        ;       "RelationshipsCreated :3 ;ContainsUpdates :true ;NodesCreated :1 ;ConstraintsAdded :1 ;PropertiesSet :6 ;LabelsAdded :1 ;"}}
                                        ;       (createClass :className "rdb22" :classType "NODE" :isAbstract? true :subClassOf ["tdb23"] :properties {"tag" "test"}  :execute? true)
                                        ;     )
                                        ;   )
                                        ; )

                                        ; (testing "Deleting all changes3"
                                        ;   (is 
                                        ;     (= (select-keys {:results [()],
                                        ;      :summary
                                        ;      {:summaryMap
                                        ;       {:relationshipsCreated 0,
                                        ;        :containsUpdates true,
                                        ;        :nodesCreated 0,
                                        ;        :nodesDeleted 2,
                                        ;        :indexesRemoved 0,
                                        ;        :labelsRemoved 0,
                                        ;        :constraintsAdded 0,
                                        ;        :propertiesSet 0,
                                        ;        :labelsAdded 0,
                                        ;        :constraintsRemoved 0,
                                        ;        :indexesAdded 0,
                                        ;        :relationshipsDeleted 1},
                                        ;       :summaryString
                                        ;       "ContainsUpdates :true ;NodesDeleted :2 ;RelationshipsDeleted :1 ;"}} [:results])
                                        ;       (select-keys (runQuery {:query "match (n {tag:{a}}) detach delete n" :parameters {"a" "test"}}) [:results]) 
                                        ;     )
                                        ;   )
                                        ; )

  (deleteDetachNodes :label "CustomFunction" :parameters {"fnName" "func"})
  (deleteDetachNodes :label "AttributeType" :parameters {"_name" "small" "_datatype" "java.lang.String"})
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
    (is 
     (= (select-keys {:results [()],
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
    (is 
     (= 
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
       "ContainsUpdates :true ;NodesCreated :1 ;PropertiesSet :2 ;LabelsAdded :1 ;"}
      (createNewNode :labels ["test"] :parameters {"name" "t-db1"} :execute? true :unique? true)
      )
     )
    )
  (testing "Error in creating a node with fewer arguements"
    (is 
     (= 
      {:summaryMap
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
      (createNewNode :labels ["test"])
      )
     )
    )
;;;;;;;;;;uuidEnabled not tested
  )

(deftest addStringToMapKeys-test
  (testing "Error in adding string to map keys(strings)"
    (is (= {"test1suff" "value1", "test2suff" "value2"}
           (addStringToMapKeys {"test1" "value1" "test2" "value2"} "suff")
           )
        )
    )

  (testing "Error in adding string to map keys(numbers)"
    (is (= {"1suff" "value1", "2suff" "value2"}
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

(deftest createRemString-test
  (testing "Error in creating remove string"
    (is (= "REMOVE n.prop1, n.prop2"
           (createRemString :varName "n" :remPropertyList ["prop1" "prop2"])
           )
        )
    )
  )

(deftest createRenameString-test
  (testing "Error in creating rename string"
    (is (= "WHERE n.newProp1 is null and n.newProp2 is null SET n.newProp1=n.prop1, n.newProp2=n.prop2 REMOVE n.prop1, n.prop2"
           (createRenameString :varName "n" :renameMap {"prop1" "newProp1" "prop2" "newProp2"})
           )
        )
    )

  (testing "Error in creating rename string"
    (is (= "n.newProp1 is null and n.newProp2 is null SET n.newProp1=n.prop1, n.newProp2=n.prop2 REMOVE n.prop1, n.prop2"
           (createRenameString :varName "n" :renameMap {"prop1" "newProp1" "prop2" "newProp2"} :addWhere? false)
           )
        )
    )
  )

(deftest editCollection-test
  (testing "Error in editing collection string APPEND"
    (is (= ["name" "prop" "value"]
           (editCollection :coll ["name" "prop"] :editType "APPEND" :editVal "value")
           )
        )
    )

  (testing "Error in editing collection string APPEND when editVal already present in editCollection"
    (is (= ["name" "prop" "prop"]
           (editCollection :coll ["name" "prop"] :editType "APPEND" :editVal "prop")
           )
        )
    )

  (testing "Error in editing collection string DELETE"
    (is (= '("name")
           (editCollection :coll ["name" "prop"] :editType "DELETE" :editVal "prop")
           )
        )
    )

  (testing "Error in editing collection string REPLACE"
    (is (= ["name" "newValue"]
           (editCollection :coll ["name" "value"] :editType "REPLACE" :editVal "value" :replaceVal "newValue")
           )
        )
    )

  (testing "Error in editing collection string REPLACE when editVal is not present in editCollection"
    (is (= ["name" "value" "newProp"]
           (editCollection :coll ["name" "value"] :editType "REPLACE" :editVal "prop" :replaceVal "newProp")
           )
        )
    )
  )

(deftest createPropListEditString-test
  (testing "Error in creating property list editing string APPEND"
    (is (= " SET n.prop = n.prop + {newProp}"
           (createPropListEditString :varName "n" :propName "prop" :editType "APPEND" :editVal "newProp")
           )
        )
    )

  (testing "Error in creating property list editing string DELETE with where true"
    (is (= "WHERE {newProp} IN n.prop SET n.prop = FILTER(x IN n.prop WHERE x <> {newProp})"
           (createPropListEditString :varName "n" :propName "prop" :editType "DELETE" :editVal "newProp")
           )
        )
    )

  (testing "Error in creating property list editing string DELETE with where false"
    (is (= " SET n.prop = FILTER(x IN n.prop WHERE x <> {newProp})"
           (createPropListEditString :varName "n" :propName "prop" :editType "DELETE" :editVal "newProp" :withWhere? false)
           )
        )
    )

  (testing "Error in creating property list editing string REPLACE with where true"
    (is (= "WHERE {newProp} IN n.prop SET n.prop = FILTER(x IN n.prop WHERE x <> {newProp}) + {repProp}"
           (createPropListEditString :varName "n" :propName "prop" :editType "REPLACE" :editVal "newProp" :replaceVal "repProp")
           )
        )
    )

  (testing "Error in creating property list editing string REPLACE with where false"
    (is (= " SET n.prop = FILTER(x IN n.prop WHERE x <> {newProp}) + {repProp}"
           (createPropListEditString :varName "n" :propName "prop" :editType "REPLACE" :editVal "newProp" :replaceVal "repProp" :withWhere? false)
           )
        )
    )
  )

(run-tests)



