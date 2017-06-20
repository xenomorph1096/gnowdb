(ns gnowdb.resources.files
  (:require [liberator.core :refer [defresource resource request-method-in]]
            [cheshire.core :refer :all] 
            [gnowdb.core :refer :all]
            [gnowdb.spec.files :refer :all])) 

(use 'clojure.walk)