(ns gnowdb.neo4j.workspaces_test
  (:require [clojure.test :refer :all]
            [gnowdb.neo4j.gneo :refer :all]
            [gnowdb.neo4j.gdriver :refer :all]
            [gnowdb.spec.workspaces :refer :all]
  )
)

(deftest instantiateGroupWorkspace-test

  (testing "Error in instantiating group workspace 1"
  	(instantiatePersonalWorkspace :displayName "user1" :description "GDB_Test")
  	(instantiateGroupWorkspace :displayName "group1" :createdBy "user1" :description "GDB_Test")
  	(is 
    	(= [1]
      (gneo/getClassInstances :className "GDB_GroupWorkspace" 
      												:parameters {"GDB_DisplayName" "group1" "GDB_Description" "GDB_Test"} 
      												:count? true)
			)
  	)
	)

	(testing "Error in instantiating group workspace when it already exists."
  	(instantiateGroupWorkspace :displayName "group1" :createdBy "user1" :description "GDB_Test")
  	(is 
    	(= [1]
      (gneo/getClassInstances :className "GDB_GroupWorkspace" 
      												:parameters {"GDB_DisplayName" "group1" "GDB_Description" "GDB_Test"} 
      												:count? true)
			)
  	)
	)

	(testing "Error in instantiating group workspace when it already exists created by some other user"
  	(instantiatePersonalWorkspace :displayName "user2" :description "GDB_Test")
  	(instantiateGroupWorkspace :displayName "group1" :createdBy "user2" :description "GDB_Test")
  	(is 
    	(= [1]
      (gneo/getClassInstances :className "GDB_GroupWorkspace" 
      												:parameters {"GDB_DisplayName" "group1" "GDB_Description" "GDB_Test"} 
      												:count? true)
			)
  	)
	)

	(testing "Error in instantiating group workspace when it already exists by some other user"
  	(instantiatePersonalWorkspace :displayName "user2" :description "GDB_Test")
  	(instantiateGroupWorkspace :displayName "group1" :createdBy "user2" :description "GDB_Test")
  	(is 
    	(= [1]
      (gneo/getClassInstances :className "GDB_GroupWorkspace" 
      												:parameters {"GDB_DisplayName" "group1" "GDB_Description" "GDB_Test"} 
      												:count? true)
			)
  	)
	)

	(deleteDetachNodes :label "GDB_GroupWorkspace" :parameters {"GDB_Description" "GDB_Test"})
	(deleteDetachNodes :label "GDB_PersonalWorkspace" :parameters {"GDB_Description" "GDB_Test"})
)