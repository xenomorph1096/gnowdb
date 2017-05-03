(defproject gnowdb "0.1.0-SNAPSHOT"
  :description "gnowdb is an implementation of gnowsys specification (
  https://www.gnu.org/software/gnowsys/). The application provides a
  framework for creating, reading, updating, and deleting nodes of a
  network. It provides functions to create nodeTypes, relationTypes, attributeTypes, metaTypes and their instances."
  :url "https://metastudio.org/gnowdb"
  :license {:name "GNU AFFERO GENERAL PUBLIC LICENSE Version 3"
            :url "https://www.gnu.org/licenses/agpl.txt"}
  :dependencies [
                 [org.clojure/clojure "1.8.0"]
                 [org.neo4j.driver/neo4j-java-driver "1.2.1"]
                 [com.orientechnologies/orientdb-client "2.2.19"]
                 [com.orientechnologies/orientdb-core "2.2.19"]
                 [com.tinkerpop.blueprints/blueprints-core "2.6.0"]
                 [com.orientechnologies/orientdb-graphdb "2.2.19"]]
  :main ^:skip-aot gnowdb.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
  :plugins [[cider/cider-nrepl "0.15.0-SNAPSHOT"]])
