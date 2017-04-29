(ns gnowdb.app
  (:require [gnowdb.coreapi :as coreapi]))

(defn createNT
  "Creates a Node Type"
  [nodeTypePropertyMap]
  (coreapi/createNewNode "nodeType" nodeTypePropertyMap))

(defn createRT
  "Creates a Relationship type"
  [relTypePropertyMap]
  (coreapi/createNewNode "relationType" relTypePropertyMap))

(defn createAT
  "Creates an Attribute type"
  [attTypePropertyMap]
  (coreapi/createNewNode "attributeType" attTypePropertyMap))

(defn getNT
  "Get NodeTypes using propertyMap"
  [propertyMap]
  (coreapi/getNodesParsed "nodeType" propertyMap))

(defn getRT
  "Get RelationTypes using propertyMap"
  [propertyMap]
  (coreapi/getNodesParsed "relationType" propertyMap))

(defn getAT
  "Get RelationTypes using propertyMap"
  [propertyMap]
  (coreapi/getNodesParsed "attributeType" propertyMap))

(defn create-nt-x
  "Creates a xType(XT) if it does not exist,
  and creates a relation between the NT and XT called hasNTX"
  [NTPropertyMap XTPropertyMap XRelType XLabel]
  (def ret "")
  (def combinedPropertyMaps (coreapi/combinePropertyMap {"NT" NTPropertyMap "XT" XTPropertyMap}))
  (def existing-nt-x (coreapi/customMatchQueryParsed (str "MATCH (NT:nodeType " ((combinedPropertyMaps 1) "NT")  " )-[:" XRelType "]-(XT:" XLabel " " ((combinedPropertyMaps 1) "XT") ") RETURN NT") (combinedPropertyMaps 0)))
  (if (= 0 (count existing-nt-x))
    (do ;;Either NT does'nt exist or XT does'nt exist or relation 'hasNTX' does'nt exist or all
      (def existingNT (getNT NTPropertyMap))
      (if (= 1 (count existingNT))
        (do ;;Unique NT exists. Proceed
          (def existingXT (coreapi/getNodesParsed XLabel XTPropertyMap))
          (if (= 1 (count existingXT))
            (do ;;Unique XT exists. Proceed
              (def cypherQuery (str "MATCH (NT:nodeType " ((combinedPropertyMaps 1) "NT") " ), (XT:" XLabel " " ((combinedPropertyMaps 1) "XT")  " ) CREATE (NT)-[:" XRelType "]->(XT)"))
              (def ret (coreapi/customUpdateQuery cypherQuery (combinedPropertyMaps 0))))
            (do ;;None or multiple XT exist
              (if (= 0 (count existingXT))
                (do ;;XT does'nt exist. Proceed
                  (def cypherQuery (str "MATCH (NT:nodeType " ((combinedPropertyMaps 1) "NT") ") CREATE (XT:" XLabel " " ((combinedPropertyMaps 1) "XT") " ) CREATE (NT)-[:" XRelType "]->(XT)"))
                  (def ret (coreapi/customUpdateQuery cypherQuery (combinedPropertyMaps 0))))
                (do ;;Multiple XT exist
                  (def ret (str (count existingXT) " XT exist(s)")))))))
        (do ;;None or multiple NT exist
          (def ret (str (count existingNT) " NT exist(s)")))))
    (do ;;NT already 'hasNTX' XT
      (def ret "NT already 'hasNTX' XT")))
  ret)


(defn create-nt-relation
  "Creates a relationType(RT) if it does not exist,
  and creates a relation between the NT and RT called 'hasNTRelation'"
  [NTPropertyMap RTPropertyMap]
  (create-nt-x NTPropertyMap RTPropertyMap "hasNTRelation" "relationType"))

(defn create-nt-attribute
  "Creates an attributeType(AT) if it does not exist,
  and creates a relation between the NT and AT called 'hasNTAttribute'"
  [NTPropertyMap ATPropertyMap]
  (create-nt-x NTPropertyMap ATPropertyMap "hasNTAttribute" "attributeType"))







