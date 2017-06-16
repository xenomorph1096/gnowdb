# Installation

Follow the steps:

## Step 1: Clojure and Leiningen

Install [Clojure 1.8 or above](https://clojure.org/guides/getting_started)

and

[Leiningen](https://leiningen.org/)

## Step 2: Neo4j

Install Neo4j Enterprise Edition.  Preferred way of doing this is to
use docker. If you already have docker installed on your OS, the
following command will create a neo4j container.


```bash
docker run \
    --publish=7474:7474 --publish=7687:7687 \
    --volume=$HOME/neo4j/data:/data \
    --volume=$HOME/neo4j/logs:/logs \
    --name neo4j
    neo4j:enterprise
```

If you do not know docker, please follow the documentation from [get
started with docker](https://docs.docker.com/get-started/)

## Step 3: gnowdb

Clone gnowdb from [git repo](https://github.com/xenomorph1096/gnowdb)

```
git clone https://github.com/xenomorph1096/gnowdb
```

Then get inside the project directory and start using gnowdb:

```bash
cd gnowdb
lein repl
```

When you run it for the first time, it will install all the
depenedencies. If you see the following `gnowdb.core` prompt, you are
all set to use gnowdb.

```
[nagarjun@xbook gnowdb]$ lein repl
nREPL server started on port 40759 on host 127.0.0.1 - nrepl://127.0.0.1:40759
REPL-y 0.3.7, nREPL 0.2.12
Clojure 1.8.0
OpenJDK 64-Bit Server VM 1.8.0_121-b13
    Docs: (doc function-name-here)
          (find-doc "part-of-name-here")
  Source: (source function-name-here)
 Javadoc: (javadoc java-object-or-class-here)
    Exit: Control+D or (exit) or (quit)
 Results: Stored in vars *1, *2, *3, an exception in *e

gnowdb.core=> 

```


## Step 4: Using gnowdb

If you already know how to use a graph database, you will find the
functions intuitive to use. Follow the [Tutorial topic](tutorial.html) for
a complete understanding of gnowdb.

