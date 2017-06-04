# Introduction to gnowdb

We are still alpha. We will remove this line when we are ready to roll.

**gnowdb** provides a gateway (API) to create, query, update and
delete a graph based database. Currently we support
[Neo4j](https://neo4j.com).  Any application could use gnowdb as a
database, specifically when your application requires networking
between various kinds of resources.

It is based on [GNOWSYS
Specification](http://www.gnu.org/software/gnowsys/) and an active
project [gstudio](https://github.com/gnowledge/gstudio/). We use the
latter as a reference model for this documentation. 



## Features

   1. Multiple users
   2. Multiple groups (users getting together to collaborate)
   3. Multiple data models, where a data model could refer to a single
   application with app specific classes (NodeTypes), relations and
   attributes.
   4. Applications can link to and access data from other applications
   to develop large composable applications from simpler components.
   5. All data remains in a single graph db as a network store with a
   possibility to make subgraphs accessible only for a single user or
   a single group or multiple groups.
   6. One data model for multiple applications
   7. Files linked to the nodes are stored and linked to a file
   system.
   8. Version control of changes (who did what and when) can be used
   optionally.
   9. Provides a RESTful API to use the gnowdb as a remote or local
   storage for your applications.
   10. API documented through this site. Follow the API through the
   Namespace section below.
