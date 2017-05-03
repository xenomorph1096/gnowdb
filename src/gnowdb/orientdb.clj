(ns gnowdb.orientdb)
(import com.tinkerpop.blueprints.impls.orient.OrientGraph)

(defn getOrientDBDetails
  "Get OrientDB Connectoin info"
  []
  ["remote:localhost/mydb" "admin" "admin"])

(defn conn
  "Sample connection"
  []
  (def orientDBDetails (getOrientDBDetails))
  (def graph (OrientGraph. (orientDBDetails 0) (orientDBDetails 1) (orientDBDetails 2))))










