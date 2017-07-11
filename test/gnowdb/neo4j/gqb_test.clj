(ns gnowdb.neo4j.gqb_test
  (:require [clojure.test :refer :all]
            [gnowdb.neo4j.gqb :refer :all]))

(deftest ddistinct_test
  (testing "ddistinct for vec and list"
    (is (= '(1 2 3)
           (ddistinct '(1 2 3 3))))
    (is (= '(1 2 3)
           (ddistinct [1 2 3 3]))))
  (testing "ddistinct for sets"
    (def mset #{1 2 3 4 12})
    (is (= mset (ddistinct mset)))))

(deftest backtick_test
  (testing "backtick with non-empty strings"
    (is (= "`ASD`"
           (backtick "ASD")))
    (is (= "`ASD`"
           (backtick "`ASD`")))
    (is (= "`ASD``"
           (backtick "ASD`")))
    (is (= "``ASD`"
           (backtick "`ASD"))))
  (testing "backtick with empty string"
    (is (= "" (backtick "")))))

(deftest remBacktick_test
  (testing "remBacktick with non-empty strings"
    (is (= "ASD"
           (remBacktick "`ASD`")))
    (is (= "`ASD"
           (remBacktick "`ASD")))
    (is (= "ASD`"
           (remBacktick "ASD`")))
    (is (= "ASD"
           (remBacktick "ASD"))))
  (testing "remBacktick with empty string"
    (is (= "" (remBacktick "")))))

(deftest isClassName?_test
  (testing "isClassName? passes"
    (is (isClassName? "asd")))
  (testing "isClassName? failures"
    (is (not (isClassName? "asd`")))
    (is (not (isClassName? "`asd`")))
    (is (not (isClassName? "`asd")))))

(deftest returnString_test
  (testing "Without change of variable name"
    (is (= " RETURN node.UUID"
           (createReturnString ["node" "UUID"])))
    (is (= " RETURN node.UUID, node2.UUID"
           (createReturnString ["node" "UUID"] ["node2" "UUID"]))))
  (testing "With change of variable name"
    (is (= " RETURN node.UUID as UUID1, node2.UUID as UUID2"
           (createReturnString ["node" "UUID" "UUID1"] ["node2" "UUID" "UUID2"])))
    (is (= " RETURN node.UUID as UUID1, node2.UUID"
           (createReturnString ["node" "UUID" "UUID1"] ["node2" "UUID"]))))
  (testing "With zero triplets"
    (is (= ""
           (createReturnString)))))

(deftest labelString_test
  (testing "With non-empty :labels collection, with empty labels and duplicate labels"
    (def res ":`A`:`B` ")
    (is (= res
           (createLabelString :labels ["A"
                                       "B"])))
    (is (= res (createLabelString :labels ["A"
                                           "B"
                                           ""])))
    (is (= res (createLabelString :labels ["A"
                                           "B"
                                           ""
                                           ""])))
    (is (= res (createLabelString :labels ["A"
                                           "B"
                                           ""
                                           "B"]))))
  (testing "With empty :labels collection"
    (is (= ""
           (createLabelString)))))
