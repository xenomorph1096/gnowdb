(ns gnowdb.neo4j.gcust
  (:gen-class)
  (:require [clojure.string :as clojure.string]
            [digest :as digest]
            [clojure.math.combinatorics :refer [nth-permutation]]
            [clj-fuzzy.metrics :refer [levenshtein]]))


(defn getCustomPassword
  [details]
  (def ^{:private true} customPassword 
    (select-keys details [:customFunctionPassword])
  )
)

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

(defn stringIsCustFunction?
  "Validates a string against customFunction template."
  [fnString]
  (isCustFunction? (eval (read-string fnString))))

(defn str-to-fn
  "Get customFunction from string"
  [fnString]
  (let [customFunction (eval (read-string fnString))] (if (isCustFunction? customFunction) customFunction (throw (Exception. (str "Function " fnString " does not match customFunction Template"))))))

(defn- combineStrings
  [str1 str2]
  (clojure.string/join "" (nth-permutation (clojure.string/split (str str1 str2) #"") (levenshtein str1 str2))))

(defn hashCustomFunction
  "Create a hash by combining a customFunction string and customFunctionPassword.
  fnString will be validated"
  [fnString]
  (str-to-fn fnString)
  (digest/sha-256 (combineStrings fnString customPassword)))

(defn execCustomFunction
  "Execute a customFunction.
  :fnString should be a string representation of a customFunction.
  :argumentListX should be first argument of customFunction should be a collection of arguments, length and values depending upon the customFunction.
  :constraintValue should be the second argument to the customFunction"
  [& {:keys [:fnString :argumentListX :constraintValue]}]
  {:pre [(string? fnString)
         (coll? argumentListX)]}
  ((str-to-fn fnString) argumentListX constraintValue))

(defn cfIsIntegrous?
  "Check the integrity of a customFunction"
  [& {:keys [:fnString :fnIntegrity]}]
  {:pre [(string? fnString)
         (string? fnIntegrity)]}
  (= fnIntegrity (hashCustomFunction fnString)))

(defn checkCustomFunction
  "Check Integrity and output of a customFunction"
  [& {:keys [:fnString :fnIntegrity :fnName :argumentListX :constraintValue]}]
  (try
    (if
        (not (cfIsIntegrous? :fnString fnString :fnIntegrity fnIntegrity))
      (throw (Exception. (str "Custom Function " fnName " is invalid."))))
    (if
        (execCustomFunction :fnString fnString :argumentListX argumentListX :constraintValue constraintValue)
      true
      (str "Arguments " argumentListX " fail(s) to satisfy '" fnName "' with '" constraintValue "'"))
    (catch Exception E (.getMessage E))))

3