(ns gnowdb.neo4j.grcs_locks_test
  (:require [clojure.test :refer :all]
            [gnowdb.neo4j.grcs_locks :refer :all]))

(deftest var-creation-deletion-tests
  (testing "Creating UUID var locks with values"
    (createVar :UUID "ABCD"
               :value (atom 1))
    (is (var? (ns-resolve 'gnowdb.neo4j.grcs_locks (symbol "VAR-ABCD"))))
    (is
     (= 1
        (deref (var-get (ns-resolve 'gnowdb.neo4j.grcs_locks (symbol "VAR-ABCD"))))
        (deref (var-get (getVar :UUID "ABCD"))))))
  (testing "Deleting UUID var lock"
    (remVar :UUID "ABCD")
    (is (nil? (getVar :UUID "ABCD")))))

(deftest lock-instantiation-finalization-tests
  (testing "Instantiation of locks"
    (is (some #(var? (first %)) (pmap #(initVars :UUIDList [%]) ["ABCD"
                                                                 "ABCD"
                                                                 "ABCD"
                                                                 "ABCD"
                                                                 "ABCD"
                                                                 "ABCD"
                                                                 "ABCD"
                                                                 "ABCD"]))))
  (testing "Finalization of locks"
    (is (some #(= 0 (first %)) (pmap #(finalizeVars :UUIDList [%]) ["ABCD"
                                                                    "ABCD"
                                                                    "ABCD"
                                                                    "ABCD"
                                                                    "ABCD"
                                                                    "ABCD"
                                                                    "ABCD"
                                                                    "ABCD"]))))
  (testing "Confirming finalization"
    (is (every? #(nil? (first %)) (pmap #(finalizeVars :UUIDList [%]) ["ABCD"
                                                                       "ABCD"
                                                                       "ABCD"
                                                                       "ABCD"
                                                                       "ABCD"
                                                                       "ABCD"
                                                                       "ABCD"
                                                                       "ABCD"])))
    (is (nil? (getVar :UUID "ABCD")))))
