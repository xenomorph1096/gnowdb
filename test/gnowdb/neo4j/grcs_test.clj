(ns gnowdb.neo4j.grcs_test
  (:require [clojure.test :refer :all]
            [gnowdb.neo4j.grcs :refer :all]))

(deftest revision-regex-test
  (testing "Testing faulty/valid rcs revision number regex pattern"
    (is 
     (every? identity
             (map #(isRevision? %)
                  ["1.1"
                   "1.9"
                   "3.99"
                   "9.1"
                   "12.7"
                   "6.9"
                   "111.19"])))
    (is (every? identity
                (map #(not (isRevision? %))
                     ["1.1.1"
                      "0.0.0"
                      ".0"
                      "5.0"
                      "0.1"
                      "12.0"
                      "0.9"
                      "asd"])))))
(run-tests)
