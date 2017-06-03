(ns gnowdb.neo4j.gcust
  (:gen-class)
  (:require [clojure.string :as clojure.string]
            [digest :as digest]
            [clojure.math.combinatorics :refer [nth-permutation]]
            [clj-fuzzy.metrics :refer [levenshtein]]))

(defn- getPassword
  "Get Password from gconf file"
  []
  (gdriver/getNeo4jDBDetails :customFunctionPassword))

(defn- arg-count
  "Get number of arguments of a function.
  https://stackoverflow.com/a/1813967/6767262"
  [f]
  (-> f class .getDeclaredMethods first .getParameterTypes alength))

(defn isCustFunction?
  "Validates an object against customFunction template.
  Object should be a function with two arguments."
  [f]
  (every? identity [(fn? f) (= 2 (arg-count f))]))

(defn str-to-fn
  "Get customFunction from string"
  [fnString]
  (let [customFunction (eval (read-string fnString))] (if (isCustFunction? customFunction) customFunction (throw (Exception. "Function does not mattch customFunction Template.")))))

(defn- combineStrings
  [str1 str2]
  (clojure.string/join "" (nth-permutation (clojure.string/split (str str1 str2) #"") (levenshtein str1 str2))))

(defn hashCustomFunction
  "Create a hash by combining a customFunction string and customFunctionPassword.
  fnString will be validated"
  [fnString]
  (str-to-fn fnString)
  (digest/sha-256 (combineStrings fnString (getPassword))))
