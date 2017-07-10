(ns gnowdb.spec.workspaces_test
  (:require [clojure.test :refer :all]
            [gnowdb.neo4j.gneo :refer :all]
            [gnowdb.neo4j.gdriver :refer :all]
            [gnowdb.spec.workspaces :refer :all]
            )
  )

(deftest instantiateGroupWorkspace-test

  (testing "Error in instantiating editable non-moderated group workspace 1"
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

  (testing "Error in instantiating editable non-moderated group workspace when it already exists."
    (instantiateGroupWorkspace :displayName "group1" :createdBy "user1" :description "GDB_Test")
    (is 
     (= [1]
        (getClassInstances :className "GDB_GroupWorkspace" 
                           :parameters {"GDB_DisplayName" "group1" "GDB_Description" "GDB_Test"} 
                           :count? true)
        )
     )
    )

  (testing "Error in instantiating editable non-moderated group workspace when it already exists created by some other user"
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

  (testing "Error in adding member to a group editable non-moderated by an admin."
    (addMemberToGroup :newMemberName "user2" :groupName "group1" :adminName "user1")
    (is 
     (= #{"user1" "user2"}
      	(set 
         (getMemberList "group1")
         )
        )
     )
    )

  (testing "Error in adding member to a editable non-moderated group by an non-admin."
    (instantiatePersonalWorkspace :displayName "user3" :description "GDB_Test")
    (addMemberToGroup :newMemberName "user3" :groupName "group1" :adminName "user2")
    (is 
     (= #{"user1" "user2" "user3"}
      	(set 
         (getMemberList "group1")
         )
        )
     )
    )

  (testing "Error in adding member to a editable non-moderated group by an non-admin."
    (is 
     (= #{"user1"}
      	(set 
         (getAdminList "group1")
         )
        )
     )
    )

  (testing "Error in adding member to a group editable non-moderated by an admin."
    (is 
     (= 	
      {:results [], 
       :summary 
       {	:summaryMap {}, 
        :summaryString "The user could not be granted Administrator permissions as it is not authorized by valid admin."
        }
       }
      (addAdminToGroup :newAdminName "user3" :groupName "group1" :adminName "user2")
      )
     )
    )

  (testing "Error in adding member to a group editable non-moderated by an admin."
    (addAdminToGroup :newAdminName "user2" :groupName "group1" :adminName "user1")
    (is 
     (= #{"user1" "user2"}
      	(set 
         (getAdminList "group1")
         )
        )
     )
    )

  (testing "Error in getting editing policy of a group workspace 1."
    (is 
     (= "Editable_Non-Moderated"
      	(getEditingPolicy "group1")
        )
     )
    )

  (testing "Error in getting group type of a group workspace 1."
    (is 
     (= "Public"
      	(getGroupType "group1")
        )
     )
    )

  (testing "Error in getting editing policy of a group workspace 1."
    (setEditingPolicy )
    (is 
     (= "Editable_Non-Moderated"
      	(getEditingPolicy "group1")
        )
     )
    )

  (testing "Error in getting group type of a group workspace 1."
    (is 
     (= "Public"
      	(getGroupType "group1")
        )
     )
    )

  (deleteDetachNodes :labels ["GDB_GroupWorkspace"] :parameters {"GDB_Description" "GDB_Test"})
  (deleteDetachNodes :labels ["GDB_PersonalWorkspace"] :parameters {"GDB_Description" "GDB_Test"})
  )

(deftest instantiatePersonalWorkspace-test
  (instantiatePersonalWorkspace :displayName "user" :memberOfGroup "Gnowledge" :createdBy "user1" :description "GDB_Test")
  (testing "Error in instantiating a personal workspace"
    (is (= 1
           (first (getClassInstances :className "GDB_PersonalWorkspace" :parameters {"GDB_DisplayName" "user"} :count? true))
           )

        )
    )
  (testing "Error in instantiating a personal workspace  when it already exists"
    (instantiatePersonalWorkspace :displayName "user" :memberOfGroup "Gnowledge" :createdBy "user1" :description "GDB_Test")
    (is (= 1
           (first (getClassInstances :className "GDB_PersonalWorkspace" :parameters {"GDB_DisplayName" "user"} :count? true))
           )
        )

    )
  (testing "Error in instantiating a personal workspace which already exists but was created by a different user"
    (instantiatePersonalWorkspace :displayName "user" :memberOfGroup "Gnowledge" :createdBy "user2" :description "GDB_Test")
    (is (= 1 
           (first (getClassInstances :className "GDB_PersonalWorkspace" :parameters {"GDB_DisplayName" "user"} :count? true))
           )
        )
    )
  
  (deleteDetachNodes :labels ["GDB_PersonalWorkspace"] :parameters {"GDB_Description" "GDB_Test"})		
  )
