(ns gnowdb.spec.workspaces_test
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
      (getClassInstances :className "GDB_GroupWorkspace" 
      												:parameters {"GDB_DisplayName" "group1" "GDB_Description" "GDB_Test"} 
      												:count? true)
			)
  	)
	)

	(testing "Error in instantiating group workspace when it already exists."
  	(instantiateGroupWorkspace :displayName "group1" :createdBy "user1" :description "GDB_Test")
  	(is 
    	(= [1]
      (getClassInstances :className "GDB_GroupWorkspace" 
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
      (getClassInstances :className "GDB_GroupWorkspace" 
      												:parameters {"GDB_DisplayName" "group1" "GDB_Description" "GDB_Test"} 
      												:count? true)
			)
  	)
	)

	(deleteDetachNodes :label "GDB_GroupWorkspace" :parameters {"GDB_Description" "GDB_Test"})
	(deleteDetachNodes :label "GDB_PersonalWorkspace" :parameters {"GDB_Description" "GDB_Test"})
)

(deftest instantiatePersonalWorkspace-test
    (instantiatePersonalWorkspace :displayName "user" :memberOfGroup "Gnowledge" :createdBy "user1" :description "GDB_Test")
	(testing "Error in instantiating a personal workspace"
		(is (= [1] 
			(getClassInstances :className "GDB_PersonalWorkspace" :parameters {"GDB_DisplayName" "user"} :count? true)
		    )

	    )
	)
	(testing "Error in instantiating a personal workspace  when it already exists"
		(instantiatePersonalWorkspace :displayName "user" :memberOfGroup "Gnowledge" :createdBy "user1" :description "GDB_Test")
		(is (= [1] 
			(getClassInstances :className "GDB_PersonalWorkspace" :parameters {"GDB_DisplayName" "user"} :count? true)
			)
		)

	)
	(testing "Error in instantiating a personal workspace which already exists but was created by a different user"
		(instantiatePersonalWorkspace :displayName "user" :memberOfGroup "Gnowledge" :createdBy "user2" :description "GDB_Test")
		(is (= [1] 
			(getClassInstances :className "GDB_PersonalWorkspace" :parameters {"GDB_DisplayName" "user"} :count? true)
			)
		)
	)
		
	(deleteDetachNodes :label "GDB_PersonalWorkspace" :parameters {"GDB_Description" "GDB_Test"})		
)
